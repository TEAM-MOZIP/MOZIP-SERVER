package com.mozip.server.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mozip.server.ai.client.SemanticMatchClient;
import com.mozip.server.ai.dto.SemanticMatchRequest;
import com.mozip.server.ai.dto.SemanticMatchResponse;
import com.mozip.server.ai.dto.SemanticMatchResult;
import com.mozip.server.bookmark.entity.Bookmark;
import com.mozip.server.bookmark.repository.BookmarkRepository;
import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Category;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyCategory;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.repository.CategoryRepository;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRegionRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.dto.PolicyPackageResponse;
import com.mozip.server.recommendation.dto.PolicyRecommendationResponse;
import com.mozip.server.region.entity.Region;
import com.mozip.server.region.repository.RegionRepository;
import com.mozip.server.user.entity.EmploymentStatus;
import com.mozip.server.user.entity.Gender;
import com.mozip.server.user.entity.HouseholdType;
import com.mozip.server.user.entity.IncomeType;
import com.mozip.server.user.entity.OAuthProvider;
import com.mozip.server.user.entity.User;
import com.mozip.server.user.entity.UserProfile;
import com.mozip.server.user.exception.UserProfileNotFoundException;
import com.mozip.server.user.repository.UserProfileRepository;
import com.mozip.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class PolicyRecommendationServiceTest {

    private static final String KEYWORD = "추천테스트정책";

    @Autowired
    private PolicyRecommendationService policyRecommendationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyEligibilityRepository policyEligibilityRepository;

    @Autowired
    private PolicyRegionRepository policyRegionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @MockitoSpyBean
    private BookmarkRepository bookmarkRepository;

    @MockitoBean
    private SemanticMatchClient semanticMatchClient;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void stubSemanticMatchClientAsUnreachableByDefault() {
        // AI 서버가 없는 테스트 환경을 기본값으로 두어, 이 필드를 stub하지 않는 기존 테스트들이
        // 전부 "AI 호출 실패 → semanticScore null → 기존 추천 결과 유지" 경로를 그대로 타게 한다.
        doThrow(new ResourceAccessException("AI 서버에 연결할 수 없습니다")).when(semanticMatchClient).match(any());
    }

    @Test
    void 여러_정책의_eligibility와_availability가_정책_ID별로_정확히_매핑된다() {
        User user = createUser("mapping@example.com", "mapping-1");
        Region seoul = regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울");
        Region busan = regionOrCreate("RECOMMEND_TEST_BUSAN", "추천테스트부산");
        createProfile(user, seoul);

        Policy nationalPolicy = createPolicy(KEYWORD + "-전국", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(nationalPolicy).build());

        Policy matchingRegionalPolicy = createPolicy(KEYWORD + "-지역일치", RegionScope.REGIONAL);
        policyRegionRepository.save(PolicyRegion.builder().policy(matchingRegionalPolicy).region(seoul).build());
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(matchingRegionalPolicy).build());

        Policy mismatchingRegionalPolicy = createPolicy(KEYWORD + "-지역불일치", RegionScope.REGIONAL);
        policyRegionRepository.save(PolicyRegion.builder().policy(mismatchingRegionalPolicy).region(busan).build());
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(mismatchingRegionalPolicy).build());

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD, null, null, null, null), false, PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(3);
        assertThat(findByPolicyId(response, nationalPolicy.getId()).eligibility().status())
                .isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(findByPolicyId(response, matchingRegionalPolicy.getId()).eligibility().status())
                .isEqualTo(EligibilityStatus.ELIGIBLE);
        assertThat(findByPolicyId(response, mismatchingRegionalPolicy.getId()).eligibility().status())
                .isEqualTo(EligibilityStatus.INELIGIBLE);
    }

    @Test
    void ELIGIBLE_NEEDS_REVIEW_INELIGIBLE_순서로_정렬된다() {
        User user = createUser("order@example.com", "order-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));

        Policy ineligiblePolicy = createPolicy(KEYWORD + "-정렬-부적격", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(ineligiblePolicy).minimumAge(200).build());
        Policy needsReviewPolicy = createPolicy(KEYWORD + "-정렬-보류", RegionScope.NATIONAL);
        Policy eligiblePolicy = createPolicy(KEYWORD + "-정렬-적격", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(eligiblePolicy).build());

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-정렬", null, null, null, null), false, PageRequest.of(0, 20));

        assertThat(response.content()).extracting(PolicyRecommendationResponse::policyId)
                .containsExactly(eligiblePolicy.getId(), needsReviewPolicy.getId(), ineligiblePolicy.getId());
    }

    @Test
    void onlyEligible이_true면_ELIGIBLE인_정책만_반환된다() {
        User user = createUser("only-eligible@example.com", "only-eligible-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));

        Policy ineligiblePolicy = createPolicy(KEYWORD + "-필터-부적격", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(ineligiblePolicy).minimumAge(200).build());
        createPolicy(KEYWORD + "-필터-보류", RegionScope.NATIONAL);
        Policy eligiblePolicy = createPolicy(KEYWORD + "-필터-적격", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(eligiblePolicy).build());

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-필터", null, null, null, null), true, PageRequest.of(0, 20));

        assertThat(response.content()).extracting(PolicyRecommendationResponse::policyId)
                .containsExactly(eligiblePolicy.getId());
    }

    @Test
    void onlyEligible이_true면_필터링된_개수_기준으로_페이지_메타데이터가_계산된다() {
        User user = createUser("only-eligible-page@example.com", "only-eligible-page-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));

        for (int i = 0; i < 3; i++) {
            Policy eligiblePolicy = createPolicy(KEYWORD + "-필터페이지-적격" + i, RegionScope.NATIONAL);
            policyEligibilityRepository.save(PolicyEligibility.builder().policy(eligiblePolicy).build());
        }
        Policy ineligiblePolicy = createPolicy(KEYWORD + "-필터페이지-부적격", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(ineligiblePolicy).minimumAge(200).build());

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-필터페이지", null, null, null, null), true,
                PageRequest.of(0, 2));

        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.content()).hasSize(2);
    }

    @Test
    void onlyEligible이_true인데_적격_정책이_없으면_빈_목록을_반환한다() {
        User user = createUser("only-eligible-empty@example.com", "only-eligible-empty-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));

        Policy ineligiblePolicy = createPolicy(KEYWORD + "-필터없음-부적격", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(ineligiblePolicy).minimumAge(200).build());
        createPolicy(KEYWORD + "-필터없음-보류", RegionScope.NATIONAL);

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-필터없음", null, null, null, null), true,
                PageRequest.of(0, 20));

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 필터_결과가_페이지_크기보다_많아도_전체_추천순으로_정렬된_뒤_페이지가_슬라이싱된다() {
        User user = createUser("page-order@example.com", "page-order-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));

        // id가 가장 낮은(=가장 먼저 생성된) 정책이 오히려 가장 낮은 우선순위(INELIGIBLE)를 갖도록 구성한다.
        // 정렬 없이 페이지네이션만 했다면 이 정책이 1페이지(size=1)에 노출되어야 하지만,
        // 전체 추천순 정렬이 적용되면 가장 마지막에 생성된 ELIGIBLE 정책이 1페이지에 노출되어야 한다.
        Policy ineligiblePolicy = createPolicy(KEYWORD + "-경계-부적격", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(ineligiblePolicy).minimumAge(200).build());
        Policy needsReviewPolicy = createPolicy(KEYWORD + "-경계-보류", RegionScope.NATIONAL);
        Policy eligiblePolicy = createPolicy(KEYWORD + "-경계-적격", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(eligiblePolicy).build());

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-경계", null, null, null, null), false, PageRequest.of(0, 1));

        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).policyId()).isEqualTo(eligiblePolicy.getId());
        assertThat(response.content().get(0).eligibility().status()).isEqualTo(EligibilityStatus.ELIGIBLE);

        // 북마크는 전체 후보(3건)가 아니라 슬라이싱된 현재 페이지(1건)에 대해서만 조회되어야 한다.
        ArgumentCaptor<List<Long>> policyIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bookmarkRepository).findBookmarkedPolicyIds(eq(user.getId()), policyIdsCaptor.capture());
        assertThat(policyIdsCaptor.getValue()).containsExactly(eligiblePolicy.getId());
        assertThat(policyIdsCaptor.getValue()).doesNotContain(needsReviewPolicy.getId(), ineligiblePolicy.getId());
    }

    @Test
    void 필터_결과_수가_페이지_크기로_정확히_나누어떨어지면_불필요한_빈_페이지가_생기지_않는다() {
        User user = createUser("exact-boundary@example.com", "exact-boundary-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        createPolicy(KEYWORD + "-정확한경계1", RegionScope.NATIONAL);
        createPolicy(KEYWORD + "-정확한경계2", RegionScope.NATIONAL);

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-정확한경계", null, null, null, null), false, PageRequest.of(0, 2));

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.content()).hasSize(2);
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isTrue();
    }

    @Test
    void 오프셋_계산이_오버플로되는_page_요청에서도_예외_없이_빈_목록을_반환한다() {
        User user = createUser("offset-overflow@example.com", "offset-overflow-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        createPolicy(KEYWORD + "-오버플로", RegionScope.NATIONAL);

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-오버플로", null, null, null, null), false,
                PageRequest.of(21474837, 100));

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.last()).isTrue();
    }

    @Test
    void 자격_조건이_없는_정책은_NEEDS_REVIEW로_응답된다() {
        User user = createUser("no-eligibility@example.com", "no-eligibility-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        createPolicy(KEYWORD + "-자격없음", RegionScope.NATIONAL);

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-자격없음", null, null, null, null), false, PageRequest.of(0, 20));

        PolicyRecommendationResponse item = response.content().get(0);
        assertThat(item.eligibility().status()).isEqualTo(EligibilityStatus.NEEDS_REVIEW);
        assertThat(item.eligibility().overallReason()).isEqualTo("자격 조건 정보가 등록되지 않음");
        assertThat(item.eligibility().conditionResults()).isEmpty();
    }

    @Test
    void 검색_조건이_추천_목록에도_적용된다() {
        User user = createUser("filter@example.com", "filter-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Policy matched = createPolicy(KEYWORD + "-필터매치", RegionScope.NATIONAL);
        createPolicy("다른정책-필터불일치", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(matched).build());

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-필터매치", null, null, null, null), false, PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).policyId()).isEqualTo(matched.getId());
    }

    @Test
    void 검색_결과가_없으면_빈_목록을_예외_없이_반환한다() {
        User user = createUser("empty-result@example.com", "empty-result-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-존재하지않는키워드", null, null, null, null), false,
                PageRequest.of(0, 20));

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isTrue();
        verify(bookmarkRepository, never()).findBookmarkedPolicyIds(any(), any());
        verify(semanticMatchClient, never()).match(any());
    }

    @Test
    void 북마크한_정책과_하지_않은_정책이_함께_있으면_각각_정확히_표시된다() {
        User user = createUser("bookmark-mixed@example.com", "bookmark-mixed-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Policy bookmarkedPolicy = createPolicy(KEYWORD + "-북마크됨", RegionScope.NATIONAL);
        Policy notBookmarkedPolicy = createPolicy(KEYWORD + "-북마크안됨", RegionScope.NATIONAL);
        bookmarkRepository.save(Bookmark.builder().user(user).policy(bookmarkedPolicy).build());

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-북마크", null, null, null, null), false, PageRequest.of(0, 20));

        assertThat(findByPolicyId(response, bookmarkedPolicy.getId()).bookmarked()).isTrue();
        assertThat(findByPolicyId(response, notBookmarkedPolicy.getId()).bookmarked()).isFalse();
    }

    @Test
    void 북마크가_하나도_없으면_모두_bookmarked가_false다() {
        User user = createUser("bookmark-none@example.com", "bookmark-none-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        createPolicy(KEYWORD + "-북마크없음", RegionScope.NATIONAL);

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-북마크없음", null, null, null, null), false, PageRequest.of(0, 20));

        assertThat(response.content()).isNotEmpty();
        assertThat(response.content()).allMatch(item -> !item.bookmarked());
    }

    @Test
    void 다른_사용자의_북마크는_반영되지_않는다() {
        User owner = createUser("bookmark-owner@example.com", "bookmark-owner-1");
        User viewer = createUser("bookmark-viewer@example.com", "bookmark-viewer-1");
        createProfile(viewer, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Policy policy = createPolicy(KEYWORD + "-타인북마크", RegionScope.NATIONAL);
        bookmarkRepository.save(Bookmark.builder().user(owner).policy(policy).build());

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                viewer.getId(), new PolicySearchRequest(KEYWORD + "-타인북마크", null, null, null, null), false, PageRequest.of(0, 20));

        assertThat(response.content().get(0).bookmarked()).isFalse();
    }

    @Test
    void 북마크_배치_조회는_한_번만_호출된다() {
        User user = createUser("bookmark-batch@example.com", "bookmark-batch-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        createPolicy(KEYWORD + "-배치1", RegionScope.NATIONAL);
        createPolicy(KEYWORD + "-배치2", RegionScope.NATIONAL);
        createPolicy(KEYWORD + "-배치3", RegionScope.NATIONAL);

        policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-배치", null, null, null, null), false, PageRequest.of(0, 20));

        verify(bookmarkRepository, times(1)).findBookmarkedPolicyIds(any(), any());
    }

    @Test
    void 페이지네이션_메타데이터가_유지된다() {
        User user = createUser("page@example.com", "page-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        createPolicy(KEYWORD + "-페이지1", RegionScope.NATIONAL);
        createPolicy(KEYWORD + "-페이지2", RegionScope.NATIONAL);

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-페이지", null, null, null, null), false, PageRequest.of(0, 1));

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
    }

    @Test
    void 사용자_프로필이_없으면_예외가_발생한다() {
        User user = createUser("no-profile@example.com", "no-profile-1");

        assertThatThrownBy(() -> policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(null, null, null, null, null), false, PageRequest.of(0, 20)))
                .isInstanceOf(UserProfileNotFoundException.class);
    }

    @Test
    void 개인화_패키지는_INELIGIBLE_정책을_제외하고_ELIGIBLE_NEEDS_REVIEW만_포함한다() {
        User user = createUser("package-status@example.com", "package-status-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Category category = createCategory("PKG_REC_STATUS", KEYWORD + "-개인화상태카테고리");

        Policy eligiblePolicy = createPolicy(KEYWORD + "-패키지상태-적격", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(eligiblePolicy).build());
        linkCategory(eligiblePolicy, category);

        Policy needsReviewPolicy = createPolicy(KEYWORD + "-패키지상태-보류", RegionScope.NATIONAL);
        linkCategory(needsReviewPolicy, category);

        Policy ineligiblePolicy = createPolicy(KEYWORD + "-패키지상태-부적격", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(ineligiblePolicy).minimumAge(200).build());
        linkCategory(ineligiblePolicy, category);

        List<PolicyPackageResponse> packages = policyRecommendationService.getPackages(user.getId());

        PolicyPackageResponse matched = findByCategoryId(packages, category.getId());
        assertThat(matched.policies()).extracting(PolicyRecommendationResponse::policyId)
                .containsExactly(eligiblePolicy.getId(), needsReviewPolicy.getId());
    }

    @Test
    void 개인화_패키지는_카테고리별로_그룹핑되어_반환된다() {
        User user = createUser("package-group@example.com", "package-group-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Category category = createCategory("PKG_REC_GROUP", KEYWORD + "-개인화그룹핑카테고리");
        Policy policy = createPolicy(KEYWORD + "-패키지그룹핑", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(policy).build());
        linkCategory(policy, category);

        List<PolicyPackageResponse> packages = policyRecommendationService.getPackages(user.getId());

        PolicyPackageResponse matched = findByCategoryId(packages, category.getId());
        assertThat(matched.categoryName()).isEqualTo(category.getName());
        assertThat(matched.policies()).extracting(PolicyRecommendationResponse::policyId).containsExactly(policy.getId());
    }

    @Test
    void 개인화_패키지는_그룹당_최대_5개까지만_포함한다() {
        User user = createUser("package-max@example.com", "package-max-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Category category = createCategory("PKG_REC_MAX", KEYWORD + "-개인화최대카테고리");
        for (int i = 0; i < 6; i++) {
            Policy policy = createPolicy(KEYWORD + "-패키지최대-" + i, RegionScope.NATIONAL);
            policyEligibilityRepository.save(PolicyEligibility.builder().policy(policy).build());
            linkCategory(policy, category);
        }

        List<PolicyPackageResponse> packages = policyRecommendationService.getPackages(user.getId());

        PolicyPackageResponse matched = findByCategoryId(packages, category.getId());
        assertThat(matched.policies()).hasSize(5);
    }

    @Test
    void 카테고리가_없는_정책은_개인화_패키지에서_제외된다() {
        User user = createUser("package-uncategorized@example.com", "package-uncategorized-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Policy uncategorized = createPolicy(KEYWORD + "-패키지미분류", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(uncategorized).build());

        List<PolicyPackageResponse> packages = policyRecommendationService.getPackages(user.getId());

        List<Long> exposedPolicyIds = packages.stream()
                .flatMap(response -> response.policies().stream())
                .map(PolicyRecommendationResponse::policyId)
                .toList();
        assertThat(exposedPolicyIds).doesNotContain(uncategorized.getId());
    }

    @Test
    void 개인화_패키지의_북마크_여부가_정확히_반영된다() {
        User user = createUser("package-bookmark@example.com", "package-bookmark-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Category category = createCategory("PKG_REC_BOOKMARK", KEYWORD + "-개인화북마크카테고리");
        Policy bookmarkedPolicy = createPolicy(KEYWORD + "-패키지북마크됨", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(bookmarkedPolicy).build());
        linkCategory(bookmarkedPolicy, category);
        Policy notBookmarkedPolicy = createPolicy(KEYWORD + "-패키지북마크안됨", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(notBookmarkedPolicy).build());
        linkCategory(notBookmarkedPolicy, category);
        bookmarkRepository.save(Bookmark.builder().user(user).policy(bookmarkedPolicy).build());

        List<PolicyPackageResponse> packages = policyRecommendationService.getPackages(user.getId());

        PolicyPackageResponse matched = findByCategoryId(packages, category.getId());
        PolicyRecommendationResponse bookmarkedItem = matched.policies().stream()
                .filter(item -> item.policyId().equals(bookmarkedPolicy.getId())).findFirst().orElseThrow();
        PolicyRecommendationResponse notBookmarkedItem = matched.policies().stream()
                .filter(item -> item.policyId().equals(notBookmarkedPolicy.getId())).findFirst().orElseThrow();
        assertThat(bookmarkedItem.bookmarked()).isTrue();
        assertThat(notBookmarkedItem.bookmarked()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 개인화_패키지는_여러_카테고리에_속한_정책의_북마크를_중복없이_한_번만_배치_조회한다() {
        User user = createUser("package-dedup@example.com", "package-dedup-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Category categoryA = createCategory("PKG_REC_DEDUP_A", KEYWORD + "-중복카테고리A");
        Category categoryB = createCategory("PKG_REC_DEDUP_B", KEYWORD + "-중복카테고리B");
        Policy policy = createPolicy(KEYWORD + "-패키지중복", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(policy).build());
        linkCategory(policy, categoryA);
        linkCategory(policy, categoryB);

        policyRecommendationService.getPackages(user.getId());

        ArgumentCaptor<List<Long>> policyIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(bookmarkRepository, times(1)).findBookmarkedPolicyIds(eq(user.getId()), policyIdsCaptor.capture());
        assertThat(policyIdsCaptor.getValue()).containsOnlyOnce(policy.getId());
    }

    @Test
    void 개인화_패키지_조회_시_프로필이_없으면_예외가_발생한다() {
        User user = createUser("package-no-profile@example.com", "package-no-profile-1");

        assertThatThrownBy(() -> policyRecommendationService.getPackages(user.getId()))
                .isInstanceOf(UserProfileNotFoundException.class);
    }

    @Test
    void AI가_정상_응답하면_policyId_기준으로_semanticScore가_응답에_포함된다() {
        User user = createUser("semantic-success@example.com", "semantic-success-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Policy policyA = createPolicy(KEYWORD + "-semantic성공A", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(policyA).build());
        Policy policyB = createPolicy(KEYWORD + "-semantic성공B", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(policyB).build());
        // eligibility가 동일해 Comparator tie-break(createdAt 역순)상 최종 응답 순서는 policyB, policyA 순이 된다.
        // AI 응답 순서는 일부러 policyA, policyB로 반대로 둬서, 배열 index로 잘못 매핑하면
        // policyA/policyB의 semanticScore가 서로 뒤바뀌어 아래 assertion이 실패하게 만든다.
        doReturn(new SemanticMatchResponse(List.of(
                new SemanticMatchResult(policyA.getId(), 0.8, List.of(), List.of()),
                new SemanticMatchResult(policyB.getId(), 0.0, List.of(), List.of())
        ))).when(semanticMatchClient).match(any());

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-semantic성공", null, null, null, null), false,
                PageRequest.of(0, 20));

        assertThat(findByPolicyId(response, policyA.getId()).semanticScore()).isEqualTo(0.8);
        assertThat(findByPolicyId(response, policyB.getId()).semanticScore()).isEqualTo(0.0);
    }

    @Test
    void AI_응답에_특정_policyId_결과가_없으면_해당_정책의_semanticScore는_null이다() {
        User user = createUser("semantic-missing@example.com", "semantic-missing-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Policy withResult = createPolicy(KEYWORD + "-semantic누락있음", RegionScope.NATIONAL);
        Policy withoutResult = createPolicy(KEYWORD + "-semantic누락없음", RegionScope.NATIONAL);
        doReturn(new SemanticMatchResponse(List.of(
                new SemanticMatchResult(withResult.getId(), 0.5, List.of(), List.of())
        ))).when(semanticMatchClient).match(any());

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-semantic누락", null, null, null, null), false,
                PageRequest.of(0, 20));

        assertThat(findByPolicyId(response, withResult.getId()).semanticScore()).isEqualTo(0.5);
        assertThat(findByPolicyId(response, withoutResult.getId()).semanticScore()).isNull();
    }

    @Test
    void AI_호출이_실패해도_기존_추천_결과와_정렬_순서가_그대로_유지된다() {
        User user = createUser("semantic-failure@example.com", "semantic-failure-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Policy ineligiblePolicy = createPolicy(KEYWORD + "-semantic실패-부적격", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(ineligiblePolicy).minimumAge(200).build());
        Policy eligiblePolicy = createPolicy(KEYWORD + "-semantic실패-적격", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(eligiblePolicy).build());
        doThrow(new ResourceAccessException("연결 실패")).when(semanticMatchClient).match(any());

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-semantic실패", null, null, null, null), false,
                PageRequest.of(0, 20));

        assertThat(response.content()).extracting(PolicyRecommendationResponse::policyId)
                .containsExactly(eligiblePolicy.getId(), ineligiblePolicy.getId());
        assertThat(response.content()).allMatch(item -> item.semanticScore() == null);
    }

    @Test
    void 페이지에_노출되는_정책이_여러_건이어도_semantic_match_batch_호출은_한_번만_수행된다() {
        User user = createUser("semantic-batch@example.com", "semantic-batch-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        createPolicy(KEYWORD + "-semantic배치1", RegionScope.NATIONAL);
        createPolicy(KEYWORD + "-semantic배치2", RegionScope.NATIONAL);
        createPolicy(KEYWORD + "-semantic배치3", RegionScope.NATIONAL);

        policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-semantic배치", null, null, null, null), false,
                PageRequest.of(0, 20));

        verify(semanticMatchClient, times(1)).match(any());
    }

    @Test
    void REGIONAL_정책의_regionCode가_semantic_match_요청에_포함된다() {
        User user = createUser("semantic-region@example.com", "semantic-region-1");
        Region seoul = regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울");
        createProfile(user, seoul);
        Policy regionalPolicy = createPolicy(KEYWORD + "-semantic지역", RegionScope.REGIONAL);
        policyRegionRepository.save(PolicyRegion.builder().policy(regionalPolicy).region(seoul).build());
        doReturn(new SemanticMatchResponse(List.of())).when(semanticMatchClient).match(any());

        policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-semantic지역", null, null, null, null), false,
                PageRequest.of(0, 20));

        ArgumentCaptor<SemanticMatchRequest> requestCaptor = ArgumentCaptor.forClass(SemanticMatchRequest.class);
        verify(semanticMatchClient).match(requestCaptor.capture());
        assertThat(requestCaptor.getValue().policies()).filteredOn(p -> p.policyId().equals(regionalPolicy.getId()))
                .extracting(p -> p.regionCodes())
                .containsExactly(List.of(seoul.getCode()));
    }

    @Test
    void PolicyEligibility가_없는_정책도_semantic_match_대상에서_제외되지_않는다() {
        User user = createUser("semantic-no-eligibility@example.com", "semantic-no-eligibility-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Policy policy = createPolicy(KEYWORD + "-semantic자격없음", RegionScope.NATIONAL);
        doReturn(new SemanticMatchResponse(List.of(
                new SemanticMatchResult(policy.getId(), 0.3, List.of(), List.of())
        ))).when(semanticMatchClient).match(any());

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-semantic자격없음", null, null, null, null), false,
                PageRequest.of(0, 20));

        ArgumentCaptor<SemanticMatchRequest> requestCaptor = ArgumentCaptor.forClass(SemanticMatchRequest.class);
        verify(semanticMatchClient).match(requestCaptor.capture());
        assertThat(requestCaptor.getValue().policies()).extracting(p -> p.policyId()).contains(policy.getId());
        assertThat(findByPolicyId(response, policy.getId()).semanticScore()).isEqualTo(0.3);
    }

    @Test
    void getPackages는_semantic_match를_호출하지_않고_semanticScore는_null이다() {
        User user = createUser("semantic-packages@example.com", "semantic-packages-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Category category = createCategory("PKG_REC_SEMANTIC", KEYWORD + "-semantic패키지카테고리");
        Policy policy = createPolicy(KEYWORD + "-semantic패키지", RegionScope.NATIONAL);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(policy).build());
        linkCategory(policy, category);

        List<PolicyPackageResponse> packages = policyRecommendationService.getPackages(user.getId());

        PolicyPackageResponse matched = findByCategoryId(packages, category.getId());
        assertThat(matched.policies()).extracting(PolicyRecommendationResponse::semanticScore)
                .containsOnlyNulls();
        verify(semanticMatchClient, never()).match(any());
    }

    private PolicyPackageResponse findByCategoryId(List<PolicyPackageResponse> packages, Long categoryId) {
        return packages.stream()
                .filter(response -> response.categoryId().equals(categoryId))
                .findFirst()
                .orElseThrow();
    }

    private Category createCategory(String code, String name) {
        return categoryRepository.save(Category.builder().code(code).name(name).build());
    }

    private void linkCategory(Policy policy, Category category) {
        entityManager.persist(PolicyCategory.builder().policy(policy).category(category).build());
    }

    private PolicyRecommendationResponse findByPolicyId(PageResponse<PolicyRecommendationResponse> response,
                                                          Long policyId) {
        return response.content().stream()
                .filter(item -> item.policyId().equals(policyId))
                .findFirst()
                .orElseThrow();
    }

    private User createUser(String email, String providerUserId) {
        return userRepository.save(User.builder()
                .email(email)
                .provider(OAuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .build());
    }

    private UserProfile createProfile(User user, Region region) {
        return userProfileRepository.save(UserProfile.builder()
                .user(user)
                .birthDate(LocalDate.of(1998, 5, 14))
                .region(region)
                .gender(Gender.FEMALE)
                .incomeType(IncomeType.MEDIAN_PERCENTAGE)
                .incomeValue(80)
                .employmentStatus(EmploymentStatus.JOB_SEEKER)
                .householdType(HouseholdType.SINGLE)
                .build());
    }

    private Region regionOrCreate(String code, String name) {
        return regionRepository.findAll().stream()
                .filter(region -> region.getCode().equals(code))
                .findFirst()
                .orElseGet(() -> regionRepository.save(Region.builder().code(code).name(name).build()));
    }

    private Policy createPolicy(String title, RegionScope regionScope) {
        Organization organization = Organization.builder()
                .name("테스트기관")
                .type("중앙부처")
                .build();
        entityManager.persist(organization);

        Policy policy = Policy.builder()
                .organization(organization)
                .title(title)
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(regionScope)
                .status(PolicyStatus.ALWAYS_OPEN)
                .build();
        return policyRepository.save(policy);
    }
}
