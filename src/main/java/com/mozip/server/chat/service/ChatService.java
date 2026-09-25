package com.mozip.server.chat.service;

import com.mozip.server.ai.dto.ChatResponseResponse;
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
import com.mozip.server.chat.evaluator.ChatKeywordExpander;
import com.mozip.server.chat.evaluator.ChatKeywordExtractor;
import com.mozip.server.chat.evaluator.ChatPolicyAudienceFilter;
import com.mozip.server.chat.evaluator.ChatPolicyMatchComparator;
import com.mozip.server.chat.evaluator.ChatPolicyRelevanceScorer;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyApplicationInfo;
import com.mozip.server.policy.evaluator.PolicyAvailabilityEvaluator;
import com.mozip.server.policy.repository.PolicyApplicationInfoRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.policy.service.ApplicationGuideService;
import com.mozip.server.policy.service.PolicyService;
import com.mozip.server.recommendation.domain.ConditionResult;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.region.entity.Region;
import com.mozip.server.region.repository.RegionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
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
    /** 특정 정책 Q&A에서 물어본 정책 카드 옆에 붙이는 비슷한 정책 수 */
    private static final int RELATED_WITH_DETAIL = 2;
    /** 조건 이어받기에 사용할 최근 사용자 메시지 수. history 전체를 넣으면 오래된 조건이 섞이고 추출 비용이 커진다. */
    private static final int HISTORY_MESSAGES_FOR_CONDITIONS = 3;
    /** 자격을 묻는 질문 표현. 이때만 이전 메시지 조건까지 모아 자격을 판정한다. */
    private static final Pattern ELIGIBILITY_QUESTION =
            Pattern.compile("받을\\s?수|자격|해당(돼|되|하|이)|신청할\\s?수|대상(이|인|에|일)");
    private static final ChatCondition EMPTY_CONDITION = new ChatCondition(null, null, null, null, null, null);

    private final ConditionExtractionService conditionExtractionService;
    private final ChatPolicySearchService chatPolicySearchService;
    private final ChatResponseGenerationService chatResponseGenerationService;
    private final PolicyService policyService;
    private final PolicyRepository policyRepository;
    private final RegionRepository regionRepository;
    private final PolicyAvailabilityEvaluator policyAvailabilityEvaluator;
    private final PolicyApplicationInfoRepository policyApplicationInfoRepository;
    private final ApplicationGuideService applicationGuideService;
    private final ChatAnswerAssembler chatAnswerAssembler;

    public ChatService(ConditionExtractionService conditionExtractionService,
                        ChatPolicySearchService chatPolicySearchService,
                        ChatResponseGenerationService chatResponseGenerationService,
                        PolicyService policyService,
                        PolicyRepository policyRepository,
                        RegionRepository regionRepository,
                        PolicyAvailabilityEvaluator policyAvailabilityEvaluator,
                        PolicyApplicationInfoRepository policyApplicationInfoRepository,
                        ApplicationGuideService applicationGuideService) {
        this.conditionExtractionService = conditionExtractionService;
        this.chatPolicySearchService = chatPolicySearchService;
        this.chatResponseGenerationService = chatResponseGenerationService;
        this.policyService = policyService;
        this.policyRepository = policyRepository;
        this.regionRepository = regionRepository;
        this.policyAvailabilityEvaluator = policyAvailabilityEvaluator;
        this.policyApplicationInfoRepository = policyApplicationInfoRepository;
        this.applicationGuideService = applicationGuideService;
        this.chatAnswerAssembler = new ChatAnswerAssembler(policyService);
    }

    public ChatResponse handle(ChatRequest request) {
        String message = request.message();
        List<ChatTurn> history = request.history();
        ConditionExtractionResponse current = conditionExtractionService.extract(message);
        List<String> keywords = ChatKeywordExpander.expand(ChatKeywordExtractor.extract(message));
        boolean currentActionable = hasActionableAxis(current);
        // 대상 집단 필터(어업인·군인 등)가 참고할 사용자 발화 — 현재 메시지 + 최근 사용자 메시지
        String userText = message + "\n" + recentUserMessages(history);

        if (currentActionable) {
            ConditionExtractionResponse extraction = mergeWithHistoryConditions(current, history);
            List<String> searchKeywords = keywords.isEmpty() ? keywordsFromHistory(history) : keywords;
            return handleConditionSearch(message, extraction, unresolvedConditionsOf(current), searchKeywords,
                    List.of(), history, userText);
        }

        // 현재 메시지에 조건이 없으면, 특정 정책 이름을 물었는지(Case C)부터 본다 — 이전 턴 조건을 이어받아
        // 목록 탐색으로 빠지면 "효행장려금 지급 알려줘" 같은 질문에 해당 정책 설명을 못 하게 된다.
        List<Policy> policies = policyRepository.findAll();
        List<Policy> titleMatches = matchPoliciesByTitle(message, policies);
        if (!titleMatches.isEmpty()) {
            return handlePolicyDetail(message, current, titleMatches, keywords, unresolvedConditionsOf(current),
                    history, userText);
        }
        // 서로 다른 정책 이름을 두 개 이상 말했으면("A랑 B 뭐가 달라?") 그 정책들을 함께 근거로 넘긴다(비교).
        List<Policy> namedPolicies = distinctTitleMatches(message, policies);
        if (namedPolicies.size() > 1) {
            return handlePolicySet(message, current, namedPolicies, unresolvedConditionsOf(current), history);
        }

        // 조건도 주제 키워드도 없는 메시지("신청 기간은?", "두 정책 비교해줘")는 이전 턴 정책에 대한 후속 질문이거나
        // 일반 질문이다. 이전 턴에 카드로 보여준 정책이 있으면 그 정책을 근거로 넘기고, 문맥 해석은 AI에 맡긴다.
        if (keywords.isEmpty()) {
            List<Policy> previousPolicies = previousTurnPolicies(history, policies);
            if (previousPolicies.size() == 1) {
                return handlePolicyDetail(message, current, previousPolicies, keywords,
                        unresolvedConditionsOf(current), history, userText);
            }
            if (previousPolicies.size() > 1) {
                return handlePolicySet(message, current, previousPolicies, unresolvedConditionsOf(current), history);
            }
            return handleGeneralOrKeywordSearch(message, current, policies, List.of(), history, userText);
        }

        ConditionExtractionResponse extraction = mergeWithHistoryConditions(current, history);
        if (hasActionableAxis(extraction)) {
            // 되묻기("교육 관련해서 궁금해")는 이전 턴의 주제(대학생 등)를 이어받아, 두 주제에 모두 맞는 정책을 먼저 고른다.
            List<String> contextKeywords = keywordsFromHistory(history).stream()
                    .filter(keyword -> !keywords.contains(keyword))
                    .toList();
            return handleConditionSearch(message, extraction, unresolvedConditionsOf(current), keywords,
                    contextKeywords, history, userText);
        }
        return handleGeneralOrKeywordSearch(message, current, policies, keywords, history, userText);
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
            List<String> keywords = ChatKeywordExpander.expand(ChatKeywordExtractor.extract(history.get(i).message()));
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

    /**
     * @param contextKeywords 이전 턴의 주제 키워드(되묻기일 때만). 현재 키워드와 이전 주제에 모두 맞는 정책을
     *                        먼저 고르고, 없으면 현재 키워드만 맞는 정책으로 넘어간다. 정렬 점수에도 함께 반영한다.
     */
    private ChatResponse handleConditionSearch(String message, ConditionExtractionResponse extraction,
                                                List<UnresolvedCondition> unresolvedConditions,
                                                List<String> keywords, List<String> contextKeywords,
                                                List<ChatTurn> history, String userText) {
        ChatCondition condition = toChatCondition(extraction);
        // (이전 주제까지 맞는 정책) → 제목·요약에 걸린 정책 → 본문(지원대상·지원내용)에만 걸린 정책 순으로 후보를 고른다.
        // 키워드가 있는데 어디에도 안 걸리면 관련 없는 정책으로 채우지 않고 빈 목록으로 둔다.
        List<Predicate<ChatPolicyMatchResult>> relevanceTiers = new ArrayList<>();
        if (!keywords.isEmpty()) {
            if (!contextKeywords.isEmpty()) {
                relevanceTiers.add(match -> ChatPolicyRelevanceScorer.matchesTitleOrSummary(match.policy(), keywords)
                        && ChatPolicyRelevanceScorer.score(match.policy(), contextKeywords) > 0);
            }
            relevanceTiers.add(match -> ChatPolicyRelevanceScorer.matchesTitleOrSummary(match.policy(), keywords));
            relevanceTiers.add(match -> ChatPolicyRelevanceScorer.score(match.policy(), keywords) > 0);
        }
        List<String> scoringKeywords = new ArrayList<>(keywords);
        scoringKeywords.addAll(contextKeywords);
        List<ChatPolicyMatchResult> top =
                selectTop(chatPolicySearchService.search(condition), scoringKeywords, relevanceTiers, userText);
        return respondWithPolicies(message, top, unresolvedConditions, history, true);
    }

    /**
     * top N 선정. INELIGIBLE과 availability가 AVAILABLE이 아닌 정책은 제외한다 — AI 계약(GroundingPolicy)에
     * availability 필드가 없어 "신청 가능한지 불확실하다"는 사실을 AI에 전달할 방법이 없기 때문이다.
     * {@code relevanceTiers}가 비어 있으면(키워드 없는 조건 탐색) 전체 후보를, 있으면 앞 단계부터 적용해
     * 처음으로 결과가 나오는 단계의 정책만 후보로 삼는다(모든 단계가 비면 빈 목록 — 관련 없는 정책으로 채우지 않음).
     * 후보가 5개 미만/0개여도 억지로 채우지 않는다.
     * 제목에 사용자가 언급하지 않은 대상 집단(어업인·군인·교직원 등)이 적힌 정책도 제외한다({@link ChatPolicyAudienceFilter}).
     */
    private List<ChatPolicyMatchResult> selectTop(List<ChatPolicyMatchResult> allMatches, List<String> keywords,
                                                  List<Predicate<ChatPolicyMatchResult>> relevanceTiers,
                                                  String userText) {
        List<ChatPolicyMatchResult> candidates = allMatches.stream()
                .filter(match -> match.eligibilityResult().overallStatus() != EligibilityStatus.INELIGIBLE)
                .filter(match -> ChatPolicyAudienceFilter.isForUser(match.policy(), userText))
                .filter(match -> policyAvailabilityEvaluator.evaluate(match.policy()).status() == PolicyAvailability.AVAILABLE)
                .map(match -> match.withRelevanceScore(ChatPolicyRelevanceScorer.score(match.policy(), keywords)))
                .toList();
        List<ChatPolicyMatchResult> pool =
                relevanceTiers.isEmpty() ? candidates : firstNonEmptyTier(candidates, relevanceTiers);
        return pool.stream()
                .sorted(ChatPolicyMatchComparator.comparator())
                .limit(TOP_N)
                .toList();
    }

    private List<ChatPolicyMatchResult> firstNonEmptyTier(List<ChatPolicyMatchResult> candidates,
                                                          List<Predicate<ChatPolicyMatchResult>> relevanceTiers) {
        for (Predicate<ChatPolicyMatchResult> tier : relevanceTiers) {
            List<ChatPolicyMatchResult> matched = candidates.stream().filter(tier).toList();
            if (!matched.isEmpty()) {
                return matched;
            }
        }
        return List.of();
    }

    /**
     * @param withEligibility 사용자가 조건을 말해 자격 판정이 의미 있을 때만 true — 조건 없이 찾은 정책은 모두 "확인 필요"로
     *                        판정되므로 카드에 자격 칩을 붙이지 않는다.
     */
    private ChatResponse respondWithPolicies(String message, List<ChatPolicyMatchResult> top,
                                             List<UnresolvedCondition> unresolvedConditions, List<ChatTurn> history,
                                             boolean withEligibility) {
        List<GroundingPolicy> groundingPolicies = ChatResponseRequestMapper.toGroundingPolicies(top);

        ChatResponseResponse answer = chatResponseGenerationService.generate(message, groundingPolicies, null,
                unresolvedConditions, ChatResponseRequestMapper.toAiChatTurns(history));

        return chatAnswerAssembler.assemble(answer, withEligibility ? eligibilityOf(top) : Map.of(), null,
                top.stream().map(ChatMatchedPolicyResponse::from).toList(),
                unresolvedConditions.stream().map(ChatUnresolvedConditionResponse::from).toList());
    }

    private Map<Long, EligibilityStatus> eligibilityOf(List<ChatPolicyMatchResult> matches) {
        Map<Long, EligibilityStatus> result = new HashMap<>();
        matches.forEach(match -> result.put(match.policy().getId(), match.eligibilityResult().overallStatus()));
        return result;
    }

    /**
     * 주제 키워드가 제목에 걸리는 정책이 있으면 조건 없이 탐색해 정책 목록을 grounding으로 전달하고,
     * 아니면 Case B(grounding 없는 일반 응답)로 처리한다.
     */
    private ChatResponse handleGeneralOrKeywordSearch(String message, ConditionExtractionResponse extraction,
                                                      List<Policy> policies, List<String> keywords,
                                                      List<ChatTurn> history, String userText) {
        List<UnresolvedCondition> unresolvedConditions = unresolvedConditionsOf(extraction);

        if (hasTitleMatchedPolicy(policies, keywords)) {
            List<ChatPolicyMatchResult> top = selectTop(chatPolicySearchService.search(EMPTY_CONDITION), keywords,
                    List.of(match -> ChatPolicyRelevanceScorer.matchesTitle(match.policy(), keywords)), userText);
            if (!top.isEmpty()) {
                return respondWithPolicies(message, top, unresolvedConditions, history, false);
            }
        }

        ChatResponseResponse answer = chatResponseGenerationService.generate(message, List.of(), null,
                unresolvedConditions, ChatResponseRequestMapper.toAiChatTurns(history));

        return chatAnswerAssembler.assemble(answer, Map.of(), null, List.of(),
                unresolvedConditions.stream().map(ChatUnresolvedConditionResponse::from).toList());
    }

    /**
     * Case C — 물어본 정책(같은 제목이 여러 개면 id가 가장 작은 것)의 상세를 grounding으로 넘겨 자세히 설명하게 하고,
     * 카드는 물어본 정책 + 비슷한 정책(같은 제목의 다른 자치구 정책, 키워드 관련 정책) 최대 2개를 내려준다.
     * 비슷한 정책은 groundingPolicies로도 넘겨 AI가 "비슷한 정책도 살펴보세요"로 안내할 수 있게 한다.
     */
    private ChatResponse handlePolicyDetail(String message, ConditionExtractionResponse current,
                                            List<Policy> titleMatches, List<String> keywords,
                                            List<UnresolvedCondition> unresolvedConditions, List<ChatTurn> history,
                                            String userText) {
        Policy primary = titleMatches.get(0);
        PolicyDetailResponse detailResponse = policyService.getPolicyDetail(primary.getId(), null);
        PolicyApplicationInfo applicationInfo =
                policyApplicationInfoRepository.findByPolicyId(primary.getId()).orElse(null);

        // "나 이거 받을 수 있어?"에 답하려면 사용자가 (이전 턴까지 포함해) 말한 조건으로 판정해야 한다.
        ChatCondition condition = conditionFor(message, current, history);
        List<Long> sameTitleIds = titleMatches.stream().map(Policy::getId).toList();
        List<ChatPolicyMatchResult> allMatches =
                chatPolicySearchService.search(condition != null ? condition : EMPTY_CONDITION);
        ChatPolicyMatchResult primaryCard = allMatches.stream()
                .filter(match -> match.policy().getId().equals(primary.getId()))
                .findFirst()
                .orElse(null);
        boolean judged = condition != null && primaryCard != null;
        List<ConditionResult> conditionResults =
                judged ? primaryCard.eligibilityResult().conditionResults() : List.of();
        // 신청 가이드가 이미 만들어져 있으면 신청 방법 답변의 단계·서류는 그걸로 채운다 — AI가 긴 원문으로 단계를
        // 다시 쓰면 답변이 길어져 timeout이 나기 쉽다. 캐시가 없으면 새로 만들지 않는다(AI 호출이 더 들기 때문).
        com.mozip.server.ai.dto.ApplicationGuideResponse guide =
                applicationGuideService.findCachedGuide(primary.getId()).orElse(null);
        PolicyDetailGrounding policyDetail = ChatResponseRequestMapper
                .toPolicyDetailGrounding(detailResponse, applicationInfo)
                .withPolicy(primary.getId(), judged ? primaryCard.eligibilityResult().overallStatus() : null,
                        conditionResults)
                .withApplicationGuideAttached(guide != null);
        List<ChatPolicyMatchResult> related = selectTop(
                allMatches.stream().filter(match -> !match.policy().getId().equals(primary.getId())).toList(),
                keywords,
                List.of(match -> sameTitleIds.contains(match.policy().getId())
                                || ChatPolicyRelevanceScorer.matchesTitleOrSummary(match.policy(), keywords)),
                userText).stream()
                .limit(RELATED_WITH_DETAIL)
                .toList();

        ChatResponseResponse answer = chatResponseGenerationService.generate(message,
                ChatResponseRequestMapper.toGroundingPolicies(related), policyDetail, unresolvedConditions,
                ChatResponseRequestMapper.toAiChatTurns(history));

        List<ChatPolicyMatchResult> shown = new ArrayList<>();
        if (primaryCard != null) {
            shown.add(primaryCard);
        }
        shown.addAll(related);
        ChatAnswerAssembler.DetailContext detail = new ChatAnswerAssembler.DetailContext(conditionResults,
                applicationInfo != null ? applicationInfo.getApplicationUrl() : null, detailResponse.sourceUrl(), guide);
        return chatAnswerAssembler.assemble(answer, condition != null ? eligibilityOf(shown) : Map.of(), detail,
                shown.stream().map(ChatMatchedPolicyResponse::from).toList(),
                unresolvedConditions.stream().map(ChatUnresolvedConditionResponse::from).toList());
    }

    /**
     * 여러 정책을 한꺼번에 근거로 넘긴다 — 정책 이름을 두 개 이상 말했거나("A랑 B 뭐가 달라?"), 이전 턴에 보여준
     * 정책들에 대한 후속 질문("두 정책 비교해줘")일 때. 비교할지·목록으로 안내할지는 AI가 질문을 보고 정한다.
     */
    private ChatResponse handlePolicySet(String message, ConditionExtractionResponse current, List<Policy> policies,
                                         List<UnresolvedCondition> unresolvedConditions, List<ChatTurn> history) {
        ChatCondition condition = conditionFor(message, current, history);
        List<Long> ids = policies.stream().map(Policy::getId).toList();
        Map<Long, ChatPolicyMatchResult> matchById = new HashMap<>();
        chatPolicySearchService.search(condition != null ? condition : EMPTY_CONDITION).stream()
                .filter(match -> ids.contains(match.policy().getId()))
                .forEach(match -> matchById.put(match.policy().getId(), match));
        List<ChatPolicyMatchResult> chosen = ids.stream().map(matchById::get).filter(Objects::nonNull).toList();
        return respondWithPolicies(message, chosen, unresolvedConditions, history, condition != null);
    }

    /**
     * 자격을 묻는 질문("나 이거 받을 수 있어?")이면 현재·최근 사용자 메시지에서 말한 조건을 돌려준다. 판정에 쓸 조건
     * (나이·지역 등)이 없거나 자격을 묻는 질문이 아니면 null — 이전 메시지 조건 추출은 AI 호출이 한 번 더 들기 때문에
     * 필요할 때만 한다.
     */
    private ChatCondition conditionFor(String message, ConditionExtractionResponse current, List<ChatTurn> history) {
        if (!ELIGIBILITY_QUESTION.matcher(message).find()) {
            return null;
        }
        ConditionExtractionResponse merged = mergeWithHistoryConditions(current, history);
        return hasActionableAxis(merged) ? toChatCondition(merged) : null;
    }

    /** 가장 최근 턴에 카드로 보여준 정책(최대 {@value #TOP_N}개). */
    private List<Policy> previousTurnPolicies(List<ChatTurn> history, List<Policy> policies) {
        if (history.isEmpty()) {
            return List.of();
        }
        List<Long> ids = history.get(history.size() - 1).policyIds();
        Map<Long, Policy> policyById = new HashMap<>();
        policies.forEach(policy -> policyById.put(policy.getId(), policy));
        return ids.stream().map(policyById::get).filter(Objects::nonNull).limit(TOP_N).toList();
    }

    /** 메시지에 제목이 통째로 들어 있는 정책을 제목마다 하나씩(id가 가장 작은 것) 고른다. */
    private List<Policy> distinctTitleMatches(String message, List<Policy> policies) {
        Map<String, Policy> byTitle = new LinkedHashMap<>();
        policies.stream()
                .filter(policy -> policy.getTitle() != null && message.contains(policy.getTitle()))
                .sorted(Comparator.comparing(Policy::getId))
                .forEach(policy -> byTitle.putIfAbsent(policy.getTitle(), policy));
        return byTitle.values().stream().limit(TOP_N).toList();
    }

    /**
     * 조건 없이 키워드만으로 정책 목록을 보여주려면 키워드가 정책 <b>제목</b>에 걸려야 한다 — "기준중위소득이 뭐야?"
     * 같은 용어 질문이, 요약·본문에만 등장하는 단어 때문에 정책 목록 응답으로 바뀌지 않게 하기 위함이다.
     */
    private boolean hasTitleMatchedPolicy(List<Policy> policies, List<String> keywords) {
        return !keywords.isEmpty()
                && policies.stream().anyMatch(policy -> ChatPolicyRelevanceScorer.matchesTitle(policy, keywords));
    }

    /**
     * 메시지가 제목을 통째로 포함하는 정책을 찾는다. 서로 다른 제목이 2개 이상 걸리면 모호한 것으로 보아
     * 빈 목록을 반환한다(추측성 특정 금지). 자치구마다 같은 이름으로 등록된 정책(예: "효행장려금 지급")은
     * 같은 정책으로 보아 id 오름차순으로 모두 반환한다.
     */
    private List<Policy> matchPoliciesByTitle(String message, List<Policy> policies) {
        List<Policy> matches = policies.stream()
                .filter(policy -> policy.getTitle() != null && message.contains(policy.getTitle()))
                .sorted(Comparator.comparing(Policy::getId))
                .toList();
        long distinctTitles = matches.stream().map(Policy::getTitle).distinct().count();
        return distinctTitles == 1 ? matches : List.of();
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
