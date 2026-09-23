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
import com.mozip.server.chat.evaluator.ChatKeywordExtractor;
import com.mozip.server.chat.evaluator.ChatPolicyMatchComparator;
import com.mozip.server.chat.evaluator.ChatPolicyRelevanceScorer;
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
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-1(조건 기반 정책 탐색)과 D-2(AI 응답 생성)를 연결하는 Chat orchestrator다.
 * intent 분기(Case A/B/C), 주제 키워드 탐색, 이전 턴 조건 이어받기, top 5 선정(INELIGIBLE 제외 +
 * 관련도 정렬), grounding 조립, AI 실패 처리를 담당한다 — 상세 규칙은 {@code docs/ai-integration/chatbot.md}
 * "Technical Design (v1, CONFIRMED)" 참고.
 */
@Service
@Transactional(readOnly = true)
public class ChatService {

    private static final int TOP_N = 5;
    /** 조건 이어받기에 사용할 최근 사용자 메시지 수. history 전체를 넣으면 오래된 조건이 섞이고 추출 비용이 커진다. */
    private static final int HISTORY_MESSAGES_FOR_CONDITIONS = 3;
    private static final ChatCondition EMPTY_CONDITION = new ChatCondition(null, null, null, null, null, null);

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
        List<ChatTurn> history = request.history();
        ConditionExtractionResponse current = conditionExtractionService.extract(message);
        List<String> keywords = ChatKeywordExtractor.extract(message);
        boolean currentActionable = hasActionableAxis(current);

        // 조건도 주제 키워드도 없는 메시지("신청 기간은?", "그거 서류는 뭐 필요해?")는 이전 턴 정책에 대한
        // 후속 질문이거나 일반 질문이다 — 기존 Case B/C 그대로 처리하고 문맥 해석은 history를 받은 AI에 맡긴다.
        if (!currentActionable && keywords.isEmpty()) {
            return handleGeneralOrPolicyDetail(message, current, List.of(), history);
        }

        ConditionExtractionResponse extraction = mergeWithHistoryConditions(current, history);
        if (hasActionableAxis(extraction)) {
            List<String> searchKeywords = keywords.isEmpty() ? keywordsFromHistory(history) : keywords;
            return handleConditionSearch(message, extraction, unresolvedConditionsOf(current), searchKeywords, history);
        }
        return handleGeneralOrPolicyDetail(message, current, keywords, history);
    }

    /**
     * Case A — actionable axis(AGE/REGION/EMPLOYMENT_STATUS/HOUSEHOLD_TYPE/INCOME)가
     * 1개 이상 있을 때만 true다. GENDER는 D-1 eligibility 평가 대상이 아니므로 여기서도
     * 판단 축에서 제외한다.
     */
    private boolean hasActionableAxis(ConditionExtractionResponse extraction) {
        return extraction != null
                && (extraction.age() != null
                || extraction.regionCode() != null
                || extraction.employmentStatus() != null
                || extraction.householdType() != null
                || extraction.incomeType() != null
                || extraction.incomeValue() != null);
    }

    /**
     * 이전 턴에서 사용자가 말한 조건(나이·지역 등)을 이어받는다. 최근 사용자 메시지를 한 번에 추출해
     * 현재 메시지에서 비어 있는 축만 채운다 — 같은 축이면 현재 메시지가 우선한다("아 사실 25살이야").
     * unresolvedConditions는 현재 메시지 기준만 유지한다(이전 턴의 미해결 조건을 다시 안내하지 않기 위함).
     */
    private ConditionExtractionResponse mergeWithHistoryConditions(ConditionExtractionResponse current,
                                                                    List<ChatTurn> history) {
        String previousMessages = recentUserMessages(history);
        if (previousMessages.isBlank()) {
            return current;
        }
        ConditionExtractionResponse previous = conditionExtractionService.extract(previousMessages);
        if (previous == null) {
            return current;
        }
        if (current == null) {
            return new ConditionExtractionResponse(previous.gender(), previous.age(), previous.regionCode(),
                    previous.employmentStatus(), previous.householdType(), previous.incomeType(),
                    previous.incomeValue(), List.of());
        }
        return new ConditionExtractionResponse(
                firstNonNull(current.gender(), previous.gender()),
                firstNonNull(current.age(), previous.age()),
                firstNonNull(current.regionCode(), previous.regionCode()),
                firstNonNull(current.employmentStatus(), previous.employmentStatus()),
                firstNonNull(current.householdType(), previous.householdType()),
                firstNonNull(current.incomeType(), previous.incomeType()),
                firstNonNull(current.incomeValue(), previous.incomeValue()),
                current.unresolvedConditions());
    }

    private String recentUserMessages(List<ChatTurn> history) {
        List<String> messages = history.stream()
                .map(ChatTurn::message)
                .filter(text -> text != null && !text.isBlank())
                .toList();
        return String.join("\n", messages.subList(Math.max(0, messages.size() - HISTORY_MESSAGES_FOR_CONDITIONS),
                messages.size()));
    }

    /** 현재 메시지에 주제 키워드가 없으면("나 20살이야") 가장 최근 사용자 메시지의 키워드를 이어받는다. */
    private List<String> keywordsFromHistory(List<ChatTurn> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            List<String> keywords = ChatKeywordExtractor.extract(history.get(i).message());
            if (!keywords.isEmpty()) {
                return keywords;
            }
        }
        return List.of();
    }

    private List<UnresolvedCondition> unresolvedConditionsOf(ConditionExtractionResponse extraction) {
        return extraction != null ? extraction.unresolvedConditions() : List.of();
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private ChatResponse handleConditionSearch(String message, ConditionExtractionResponse extraction,
                                                List<UnresolvedCondition> unresolvedConditions,
                                                List<String> keywords, List<ChatTurn> history) {
        ChatCondition condition = toChatCondition(extraction);
        List<ChatPolicyMatchResult> top = selectTop(chatPolicySearchService.search(condition), keywords,
                match -> match.relevanceScore() > 0, false);
        return respondWithPolicies(message, top, unresolvedConditions, history);
    }

    /**
     * top N 선정. INELIGIBLE과 availability가 AVAILABLE이 아닌 정책은 제외한다 — AI 계약(GroundingPolicy)에
     * availability 필드가 없어 "신청 가능한지 불확실하다"는 사실을 AI에 전달할 방법이 없기 때문이다.
     * {@code isRelevant}를 만족하는 정책이 하나라도 있으면 그 정책들만 후보로 삼고, 없으면
     * {@code requireRelevance}가 false일 때만 전체 후보로 대체한다. 후보가 5개 미만/0개여도 억지로 채우지 않는다.
     */
    private List<ChatPolicyMatchResult> selectTop(List<ChatPolicyMatchResult> allMatches, List<String> keywords,
                                                  Predicate<ChatPolicyMatchResult> isRelevant,
                                                  boolean requireRelevance) {
        List<ChatPolicyMatchResult> candidates = allMatches.stream()
                .filter(match -> match.eligibilityResult().overallStatus() != EligibilityStatus.INELIGIBLE)
                .filter(match -> policyAvailabilityEvaluator.evaluate(match.policy()).status() == PolicyAvailability.AVAILABLE)
                .map(match -> match.withRelevanceScore(ChatPolicyRelevanceScorer.score(match.policy(), keywords)))
                .toList();
        List<ChatPolicyMatchResult> relevant = candidates.stream()
                .filter(isRelevant)
                .toList();
        List<ChatPolicyMatchResult> pool = !relevant.isEmpty() || requireRelevance ? relevant : candidates;
        return pool.stream()
                .sorted(ChatPolicyMatchComparator.comparator())
                .limit(TOP_N)
                .toList();
    }

    private ChatResponse respondWithPolicies(String message, List<ChatPolicyMatchResult> top,
                                             List<UnresolvedCondition> unresolvedConditions, List<ChatTurn> history) {
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
     * Case B(일반 fallback)/Case C(특정 정책 Q&A)/키워드 탐색을 처리한다.
     * <ol>
     *   <li>정책 제목이 메시지 안에서 정확히 하나만 매칭되면 Case C(해당 정책 상세 grounding)</li>
     *   <li>아니면 주제 키워드가 제목에 걸리는 정책이 있을 때 조건 없이 탐색해 정책 목록을 grounding으로 전달</li>
     *   <li>둘 다 아니면 Case B(grounding 없는 일반 응답)</li>
     * </ol>
     */
    private ChatResponse handleGeneralOrPolicyDetail(String message, ConditionExtractionResponse extraction,
                                                       List<String> keywords, List<ChatTurn> history) {
        List<UnresolvedCondition> unresolvedConditions = unresolvedConditionsOf(extraction);
        List<Policy> policies = policyRepository.findAll();

        PolicyDetailGrounding policyDetail = matchPolicyByTitle(message, policies)
                .map(policy -> policyService.getPolicyDetail(policy.getId(), null))
                .map(ChatResponseRequestMapper::toPolicyDetailGrounding)
                .orElse(null);

        if (policyDetail == null && hasTitleMatchedPolicy(policies, keywords)) {
            List<ChatPolicyMatchResult> top = selectTop(chatPolicySearchService.search(EMPTY_CONDITION), keywords,
                    match -> ChatPolicyRelevanceScorer.matchesTitle(match.policy(), keywords), true);
            if (!top.isEmpty()) {
                return respondWithPolicies(message, top, unresolvedConditions, history);
            }
        }

        String reply = chatResponseGenerationService.generate(message, List.of(), policyDetail, unresolvedConditions,
                ChatResponseRequestMapper.toAiChatTurns(history));

        return new ChatResponse(reply, List.of(),
                unresolvedConditions.stream().map(ChatUnresolvedConditionResponse::from).toList());
    }

    /**
     * 조건 없이 키워드만으로 정책 목록을 보여주려면 키워드가 정책 <b>제목</b>에 걸려야 한다 — "기준중위소득이 뭐야?"
     * 같은 용어 질문이, 요약·지원대상 본문에만 등장하는 단어 때문에 정책 목록 응답으로 바뀌지 않게 하기 위함이다.
     */
    private boolean hasTitleMatchedPolicy(List<Policy> policies, List<String> keywords) {
        return !keywords.isEmpty()
                && policies.stream().anyMatch(policy -> ChatPolicyRelevanceScorer.matchesTitle(policy, keywords));
    }

    /**
     * 전체 정책을 메모리에서 "메시지가 제목을 포함하는지"로 판정한다 — SQL로 이 방향("message LIKE %title%",
     * title이 패턴 쪽)을 표현하려면 title에 포함된 SQL LIKE 와일드카드(%, _) 문자를 이스케이프해야 하는데,
     * 그 복잡성 대비 실익이 없다. 정책 수천 건 규모에서도 문자열 포함 검사라 비용이 작다. 정확히 1건만 매칭될
     * 때만 채택하고, 0건/다건이면 모호한 것으로 보아 매칭하지 않는다(추측성 특정 금지).
     */
    private Optional<Policy> matchPolicyByTitle(String message, List<Policy> policies) {
        List<Policy> matches = policies.stream()
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
