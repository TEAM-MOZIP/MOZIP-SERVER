package com.mozip.server.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mozip.server.bookmark.entity.Bookmark;
import com.mozip.server.bookmark.repository.BookmarkRepository;
import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyRegion;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRegionRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.recommendation.domain.EligibilityStatus;
import com.mozip.server.recommendation.dto.PolicyRecommendationResponse;
import com.mozip.server.region.entity.Region;
import com.mozip.server.region.repository.RegionRepository;
import com.mozip.server.user.entity.EmploymentStatus;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

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

    @MockitoSpyBean
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private EntityManager entityManager;

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
                user.getId(), new PolicySearchRequest(KEYWORD, null, null, null), PageRequest.of(0, 20));

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
                user.getId(), new PolicySearchRequest(KEYWORD + "-정렬", null, null, null), PageRequest.of(0, 20));

        assertThat(response.content()).extracting(PolicyRecommendationResponse::policyId)
                .containsExactly(eligiblePolicy.getId(), needsReviewPolicy.getId(), ineligiblePolicy.getId());
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
                user.getId(), new PolicySearchRequest(KEYWORD + "-경계", null, null, null), PageRequest.of(0, 1));

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
                user.getId(), new PolicySearchRequest(KEYWORD + "-정확한경계", null, null, null), PageRequest.of(0, 2));

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
                user.getId(), new PolicySearchRequest(KEYWORD + "-오버플로", null, null, null),
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
                user.getId(), new PolicySearchRequest(KEYWORD + "-자격없음", null, null, null), PageRequest.of(0, 20));

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
                user.getId(), new PolicySearchRequest(KEYWORD + "-필터매치", null, null, null), PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).policyId()).isEqualTo(matched.getId());
    }

    @Test
    void 검색_결과가_없으면_빈_목록을_예외_없이_반환한다() {
        User user = createUser("empty-result@example.com", "empty-result-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-존재하지않는키워드", null, null, null),
                PageRequest.of(0, 20));

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isTrue();
        verify(bookmarkRepository, never()).findBookmarkedPolicyIds(any(), any());
    }

    @Test
    void 북마크한_정책과_하지_않은_정책이_함께_있으면_각각_정확히_표시된다() {
        User user = createUser("bookmark-mixed@example.com", "bookmark-mixed-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        Policy bookmarkedPolicy = createPolicy(KEYWORD + "-북마크됨", RegionScope.NATIONAL);
        Policy notBookmarkedPolicy = createPolicy(KEYWORD + "-북마크안됨", RegionScope.NATIONAL);
        bookmarkRepository.save(Bookmark.builder().user(user).policy(bookmarkedPolicy).build());

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-북마크", null, null, null), PageRequest.of(0, 20));

        assertThat(findByPolicyId(response, bookmarkedPolicy.getId()).bookmarked()).isTrue();
        assertThat(findByPolicyId(response, notBookmarkedPolicy.getId()).bookmarked()).isFalse();
    }

    @Test
    void 북마크가_하나도_없으면_모두_bookmarked가_false다() {
        User user = createUser("bookmark-none@example.com", "bookmark-none-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        createPolicy(KEYWORD + "-북마크없음", RegionScope.NATIONAL);

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-북마크없음", null, null, null), PageRequest.of(0, 20));

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
                viewer.getId(), new PolicySearchRequest(KEYWORD + "-타인북마크", null, null, null), PageRequest.of(0, 20));

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
                user.getId(), new PolicySearchRequest(KEYWORD + "-배치", null, null, null), PageRequest.of(0, 20));

        verify(bookmarkRepository, times(1)).findBookmarkedPolicyIds(any(), any());
    }

    @Test
    void 페이지네이션_메타데이터가_유지된다() {
        User user = createUser("page@example.com", "page-1");
        createProfile(user, regionOrCreate("RECOMMEND_TEST_SEOUL", "추천테스트서울"));
        createPolicy(KEYWORD + "-페이지1", RegionScope.NATIONAL);
        createPolicy(KEYWORD + "-페이지2", RegionScope.NATIONAL);

        PageResponse<PolicyRecommendationResponse> response = policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(KEYWORD + "-페이지", null, null, null), PageRequest.of(0, 1));

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
    }

    @Test
    void 사용자_프로필이_없으면_예외가_발생한다() {
        User user = createUser("no-profile@example.com", "no-profile-1");

        assertThatThrownBy(() -> policyRecommendationService.getRecommendations(
                user.getId(), new PolicySearchRequest(null, null, null, null), PageRequest.of(0, 20)))
                .isInstanceOf(UserProfileNotFoundException.class);
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
                .gender("F")
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
