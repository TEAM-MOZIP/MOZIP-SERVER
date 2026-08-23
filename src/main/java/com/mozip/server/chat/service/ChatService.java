package com.mozip.server.chat.service;

import com.mozip.server.ai.dto.ConditionExtractionResponse;
import com.mozip.server.ai.dto.GroundingPolicy;
import com.mozip.server.ai.dto.PolicyDetailGrounding;
import com.mozip.server.ai.dto.UnresolvedCondition;
import com.mozip.server.ai.mapper.ChatResponseRequestMapper;
import com.mozip.server.ai.service.ChatResponseGenerationService;
import com.mozip.server.ai.service.ConditionExtractionService;
import com.mozip.server.chat.domain.ChatCondition;
import com.mozip.server.chat.dto.ChatMatchedPolicyResponse;
import com.mozip.server.chat.dto.ChatPolicyMatchResult;
import com.mozip.server.chat.dto.ChatRequest;
import com.mozip.server.chat.dto.ChatResponse;
import com.mozip.server.chat.dto.ChatTurn;
import com.mozip.server.chat.dto.ChatUnresolvedConditionResponse;
import com.mozip.server.chat.evaluator.ChatPolicyMatchComparator;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.policy.service.PolicyService;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.region.entity.Region;
import com.mozip.server.region.repository.RegionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-1(조건 기반 정책 탐색)과 D-2(AI 응답 생성)를 연결하는 Chat orchestrator다.
 * intent 분기(Case A/B/C), top 5 선정(INELIGIBLE 제외 + 정렬), grounding 조립,
 * AI 실패 처리를 담당한다 — 상세 규칙은 {@code docs/ai-integration/chatbot.md}
 * "Technical Design (v1, CONFIRMED)" 참고.
 */
@Service
@Transactional(readOnly = true)
public class ChatService {

    private static final int TOP_N = 5;

    private final ConditionExtractionService conditionExtractionService;
    private final ChatPolicySearchService chatPolicySearchService;
    private final ChatResponseGenerationService chatResponseGenerationService;
    private final PolicyService policyService;
    private final PolicyRepository policyRepository;
    private final RegionRepository regionRepository;
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator;

    public ChatService(ConditionExtractionService conditionExtractionService,
                        ChatPolicySearchService chatPolicySearchService,
                        ChatResponseGenerationService chatResponseGenerationService,
                        PolicyService policyService,
                        PolicyRepository policyRepository,
                        RegionRepository regionRepository,
                        PolicyAvailabilityEvaluator policyAvailabilityEvaluator) {
        this.conditionExtractionService = conditionExtractionService;
        this.chatPolicySearchService = chatPolicySearchService;
        this.chatResponseGenerationService = chatResponseGenerationService;
        this.policyService = policyService;
        this.policyRepository = policyRepository;
        this.regionRepository = regionRepository;
        this.policyAvailabilityEvaluator = policyAvailabilityEvaluator;
    }

    public ChatResponse handle(ChatRequest request) {
        String message = request.message();
        ConditionExtractionResponse extraction = conditionExtractionService.extract(message);

        if (extraction != null && hasActionableAxis(extraction)) {
            return handleConditionSearch(message, extraction, request.history());
        }
        return handleGeneralOrPolicyDetail(message, extraction, request.history());
    }

    /**
     * Case A — actionable axis(AGE/REGION/EMPLOYMENT_STATUS/HOUSEHOLD_TYPE/INCOME)가
     * 1개 이상 있을 때만 true다. GENDER는 D-1 eligibility 평가 대상이 아니므로 여기서도
     * 판단 축에서 제외한다.
     */
    private boolean hasActionableAxis(ConditionExtractionResponse extraction) {
        return extraction.age() != null
                || extraction.regionCode() != null
                || extraction.employmentStatus() != null
                || extraction.householdType() != null
                || extraction.incomeType() != null
                || extraction.incomeValue() != null;
    }

    private ChatResponse handleConditionSearch(String message, ConditionExtractionResponse extraction,
                                                List<ChatTurn> history) {
        ChatCondition condition = toChatCondition(extraction);
        List<ChatPolicyMatchResult> allMatches = chatPolicySearchService.search(condition);

        // availability가 NEEDS_REVIEW인 정책도 챗봇에서는 제외한다 — AI 계약(GroundingPolicy)에
        // availability 필드가 없어 "신청 가능한지 불확실하다"는 사실을 AI에 전달할 방법이 없기
        // 때문이다. AVAILABLE만 통과시켜 후보가 5개 미만/0개여도 억지로 채우지 않는다.
        List<ChatPolicyMatchResult> top = allMatches.stream()
                .filter(match -> match.eligibilityResult().overallStatus() != EligibilityStatus.INELIGIBLE)
                .filter(match -> policyAvailabilityEvaluator.evaluate(match.policy()).status() == PolicyAvailability.AVAILABLE)
                .sorted(ChatPolicyMatchComparator.comparator())
                .limit(TOP_N)
                .toList();

        List<UnresolvedCondition> unresolvedConditions = extraction.unresolvedConditions();
        List<GroundingPolicy> groundingPolicies = ChatResponseRequestMapper.toGroundingPolicies(top);

        String reply = chatResponseGenerationService.generate(message, groundingPolicies, null, unresolvedConditions,
                ChatResponseRequestMapper.toAiChatTurns(history));

        return new ChatResponse(
                reply,
                top.stream().map(ChatMatchedPolicyResponse::from).toList(),
                unresolvedConditions.stream().map(ChatUnresolvedConditionResponse::from).toList()
        );
    }

    /**
     * Case B(일반 fallback)/Case C(특정 정책 Q&A)는 grounding 조립 형태만 다르고 나머지
     * 흐름(AI 응답 생성 호출, 빈 matchedPolicies)이 같아 하나의 경로로 처리한다. 정책
     * 제목이 메시지 안에서 정확히 하나만 매칭되면 Case C, 아니면 Case B로 수렴한다.
     */
    private ChatResponse handleGeneralOrPolicyDetail(String message, ConditionExtractionResponse extraction,
                                                       List<ChatTurn> history) {
        List<UnresolvedCondition> unresolvedConditions = extraction != null ? extraction.unresolvedConditions() : List.of();

        PolicyDetailGrounding policyDetail = matchPolicyByTitle(message)
                .map(policy -> policyService.getPolicyDetail(policy.getId(), null))
                .map(ChatResponseRequestMapper::toPolicyDetailGrounding)
                .orElse(null);

        String reply = chatResponseGenerationService.generate(message, List.of(), policyDetail, unresolvedConditions,
                ChatResponseRequestMapper.toAiChatTurns(history));

        return new ChatResponse(reply, List.of(),
                unresolvedConditions.stream().map(ChatUnresolvedConditionResponse::from).toList());
    }

    /**
     * 정책 개수가 적어(seed 기준 20건) 전체 조회 후 메모리에서 "메시지가 제목을
     * 포함하는지"를 판정하는 방식을 택했다 — SQL로 이 방향("message LIKE %title%",
     * title이 패턴 쪽)을 표현하려면 title에 포함된 SQL LIKE 와일드카드(%, _) 문자를
     * 이스케이프해야 하는데, 그 복잡성 대비 실익이 없다. 정확히 1건만 매칭될 때만
     * 채택하고, 0건/다건이면 모호한 것으로 보아 매칭하지 않는다(추측성 특정 금지).
     */
    private Optional<Policy> matchPolicyByTitle(String message) {
        List<Policy> matches = policyRepository.findAll().stream()
                .filter(policy -> message.contains(policy.getTitle()))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private ChatCondition toChatCondition(ConditionExtractionResponse extraction) {
        Long regionId = resolveRegionId(extraction.regionCode());
        return new ChatCondition(extraction.age(), regionId, extraction.employmentStatus(), extraction.householdType(),
                extraction.incomeType(), extraction.incomeValue());
    }

    private Long resolveRegionId(String regionCode) {
        if (regionCode == null) {
            return null;
        }
        return regionRepository.findByCode(regionCode).map(Region::getId).orElse(null);
    }
}
