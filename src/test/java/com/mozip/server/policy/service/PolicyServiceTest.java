package com.mozip.server.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mozip.server.bookmark.entity.Bookmark;
import com.mozip.server.bookmark.repository.BookmarkRepository;
import com.mozip.server.global.dto.PageResponse;
import com.mozip.server.policy.domain.AgeGroup;
import com.mozip.server.policy.domain.PolicyAvailability;
import com.mozip.server.policy.dto.PolicyDetailResponse;
import com.mozip.server.policy.dto.PolicySearchRequest;
import com.mozip.server.policy.dto.PolicySummaryResponse;
import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Organization;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyEligibility;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import com.mozip.server.policy.exception.PolicyNotFoundException;
import com.mozip.server.policy.repository.PolicyEligibilityRepository;
import com.mozip.server.policy.repository.PolicyRepository;
import com.mozip.server.user.entity.OAuthProvider;
import com.mozip.server.user.entity.User;
import com.mozip.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class PolicyServiceTest {

    private static final String KEYWORD = "가용성테스트정책";

    @Autowired
    private PolicyService policyService;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PolicyEligibilityRepository policyEligibilityRepository;

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 목록_조회_시_AVAILABLE_정책과_UNAVAILABLE_정책이_각각_올바르게_응답된다() {
        Policy availablePolicy = createPolicy(KEYWORD + "-상시", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS,
                null, null);
        Policy unavailablePolicy = createPolicy(KEYWORD + "-마감", PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.now().minusYears(1), LocalDate.now().minusMonths(1));

        PageResponse<PolicySummaryResponse> response = policyService.searchPolicies(
                new PolicySearchRequest(KEYWORD, null, null, null, null), PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(2);
        assertThat(findById(response, availablePolicy.getId()).availability().status())
                .isEqualTo(PolicyAvailability.AVAILABLE);
        assertThat(findById(response, unavailablePolicy.getId()).availability().status())
                .isEqualTo(PolicyAvailability.UNAVAILABLE);
    }

    @Test
    void 상세_조회_시_availability가_올바르게_응답된다() {
        Policy policy = createPolicy(KEYWORD + "-상세", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);

        PolicyDetailResponse response = policyService.getPolicyDetail(policy.getId(), null);

        assertThat(response.availability().status()).isEqualTo(PolicyAvailability.AVAILABLE);
    }

    @Test
    void 존재하지_않는_정책_상세_조회_시_예외가_발생한다() {
        assertThatThrownBy(() -> policyService.getPolicyDetail(999999L, null))
                .isInstanceOf(PolicyNotFoundException.class);
    }

    @Test
    void 비로그인_상태로_상세_조회_시_bookmarked는_false다() {
        Policy policy = createPolicy(KEYWORD + "-비로그인상세", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);

        PolicyDetailResponse response = policyService.getPolicyDetail(policy.getId(), null);

        assertThat(response.bookmarked()).isFalse();
    }

    @Test
    void 로그인_사용자가_북마크한_정책은_상세_조회에서_bookmarked가_true다() {
        User user = createUser("policy-detail-bookmarked@example.com", "policy-detail-bookmarked-1");
        Policy policy = createPolicy(KEYWORD + "-북마크있음", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        bookmarkRepository.save(Bookmark.builder().user(user).policy(policy).build());

        PolicyDetailResponse response = policyService.getPolicyDetail(policy.getId(), user.getId());

        assertThat(response.bookmarked()).isTrue();
    }

    @Test
    void 로그인_사용자여도_북마크하지_않은_정책은_상세_조회에서_bookmarked가_false다() {
        User user = createUser("policy-detail-not-bookmarked@example.com", "policy-detail-not-bookmarked-1");
        Policy policy = createPolicy(KEYWORD + "-북마크없음", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);

        PolicyDetailResponse response = policyService.getPolicyDetail(policy.getId(), user.getId());

        assertThat(response.bookmarked()).isFalse();
    }

    @Test
    void 다른_사용자가_북마크한_정책은_상세_조회의_bookmarked에_반영되지_않는다() {
        User owner = createUser("policy-detail-owner@example.com", "policy-detail-owner-1");
        User viewer = createUser("policy-detail-viewer@example.com", "policy-detail-viewer-1");
        Policy policy = createPolicy(KEYWORD + "-타인북마크", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        bookmarkRepository.save(Bookmark.builder().user(owner).policy(policy).build());

        PolicyDetailResponse response = policyService.getPolicyDetail(policy.getId(), viewer.getId());

        assertThat(response.bookmarked()).isFalse();
    }

    @Test
    void 공개_추천_목록은_AVAILABLE_정책을_UNAVAILABLE_정책보다_먼저_반환한다() {
        // id가 더 낮은(=먼저 생성된) 정책이 UNAVAILABLE이 되도록 구성해, 생성 순서가 아니라
        // 신청 가능 여부 기준으로 정렬됨을 검증한다.
        Policy unavailablePolicy = createPolicy(KEYWORD + "-공개-마감", PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.now().minusYears(1), LocalDate.now().minusMonths(1));
        Policy availablePolicy = createPolicy(KEYWORD + "-공개-상시", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS,
                null, null);

        PageResponse<PolicySummaryResponse> response = policyService.getRecommendedPolicies(
                new PolicySearchRequest(KEYWORD + "-공개", null, null, null, null), PageRequest.of(0, 20));

        assertThat(response.content()).extracting(PolicySummaryResponse::id)
                .containsExactly(availablePolicy.getId(), unavailablePolicy.getId());
    }

    @Test
    void 공개_추천_목록은_필터_결과가_페이지_크기보다_많아도_전체_기준으로_정렬된_뒤_페이지가_슬라이싱된다() {
        // id가 가장 낮은 정책이 UNAVAILABLE, id가 가장 높은(가장 나중에 생성된) 정책이 AVAILABLE이 되도록 구성한다.
        // 정렬 없이 페이지네이션만 했다면 이 UNAVAILABLE 정책이 1페이지(size=1)에 노출되어야 하지만,
        // 신청 가능 여부 기준 전체 정렬이 적용되면 AVAILABLE 정책이 1페이지에 노출되어야 한다.
        createPolicy(KEYWORD + "-경계-마감", PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.now().minusYears(1), LocalDate.now().minusMonths(1));
        createPolicy(KEYWORD + "-경계-보류", PolicyStatus.OPEN, ApplicationType.UNKNOWN, null, null);
        Policy availablePolicy = createPolicy(KEYWORD + "-경계-상시", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS,
                null, null);

        PageResponse<PolicySummaryResponse> response = policyService.getRecommendedPolicies(
                new PolicySearchRequest(KEYWORD + "-경계", null, null, null, null), PageRequest.of(0, 1));

        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).id()).isEqualTo(availablePolicy.getId());
        assertThat(response.content().get(0).availability().status()).isEqualTo(PolicyAvailability.AVAILABLE);
    }

    @Test
    void 공개_추천_목록에도_검색_조건이_적용된다() {
        Policy matched = createPolicy(KEYWORD + "-필터매치", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        createPolicy("다른정책-필터불일치", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);

        PageResponse<PolicySummaryResponse> response = policyService.getRecommendedPolicies(
                new PolicySearchRequest(KEYWORD + "-필터매치", null, null, null, null), PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).id()).isEqualTo(matched.getId());
    }

    @Test
    void 공개_추천_목록은_두_번째_페이지에서_남은_정책만_정확히_반환한다() {
        createPolicy(KEYWORD + "-페이지A", PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(10));
        createPolicy(KEYWORD + "-페이지B", PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(20));
        Policy latest = createPolicy(KEYWORD + "-페이지C", PolicyStatus.OPEN, ApplicationType.PERIOD,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));

        PageResponse<PolicySummaryResponse> response = policyService.getRecommendedPolicies(
                new PolicySearchRequest(KEYWORD + "-페이지", null, null, null, null), PageRequest.of(1, 2));

        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.content()).extracting(PolicySummaryResponse::id).containsExactly(latest.getId());
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isTrue();
    }

    @Test
    void 공개_추천_목록은_오프셋_계산이_오버플로되는_page_요청에서도_예외_없이_빈_목록을_반환한다() {
        createPolicy(KEYWORD + "-오버플로", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);

        PageResponse<PolicySummaryResponse> response = policyService.getRecommendedPolicies(
                new PolicySearchRequest(KEYWORD + "-오버플로", null, null, null, null), PageRequest.of(21474837, 100));

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.last()).isTrue();
    }

    @Test
    void 공개_추천_검색_결과가_없으면_빈_목록을_예외_없이_반환한다() {
        PageResponse<PolicySummaryResponse> response = policyService.getRecommendedPolicies(
                new PolicySearchRequest(KEYWORD + "-존재하지않는키워드", null, null, null, null), PageRequest.of(0, 20));

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isTrue();
    }

    @Test
    void 연령_구간이_겹치는_정책만_ageGroup_필터에_매칭된다() {
        Policy matched = createPolicy(KEYWORD + "-연령매치", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(matched).minimumAge(25).maximumAge(29).build());
        Policy notMatched = createPolicy(KEYWORD + "-연령매치", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(notMatched).minimumAge(30).maximumAge(34).build());

        PageResponse<PolicySummaryResponse> response = policyService.searchPolicies(
                new PolicySearchRequest(KEYWORD + "-연령매치", null, null, null, AgeGroup.AGE_25_29), PageRequest.of(0, 20));

        assertThat(response.content()).extracting(PolicySummaryResponse::id).containsExactly(matched.getId());
    }

    @Test
    void 연령_제한_근거가_없는_정책은_ageGroup_필터에서도_포함된다() {
        Policy noEligibilityRecord = createPolicy(KEYWORD + "-연령없음", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        Policy bothNull = createPolicy(KEYWORD + "-연령없음", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(bothNull).build());
        Policy outOfRange = createPolicy(KEYWORD + "-연령없음", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(outOfRange).minimumAge(100).build());

        PageResponse<PolicySummaryResponse> response = policyService.searchPolicies(
                new PolicySearchRequest(KEYWORD + "-연령없음", null, null, null, AgeGroup.AGE_25_29), PageRequest.of(0, 20));

        assertThat(response.content()).extracting(PolicySummaryResponse::id)
                .containsExactlyInAnyOrder(noEligibilityRecord.getId(), bothNull.getId());
    }

    @Test
    void 한쪽_경계만_있는_정책도_구간과_겹치면_ageGroup_필터에_포함된다() {
        Policy overlapping = createPolicy(KEYWORD + "-한쪽경계", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(overlapping).minimumAge(60).build());
        Policy nonOverlapping = createPolicy(KEYWORD + "-한쪽경계", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(nonOverlapping).minimumAge(70).build());

        PageResponse<PolicySummaryResponse> response = policyService.searchPolicies(
                new PolicySearchRequest(KEYWORD + "-한쪽경계", null, null, null, AgeGroup.AGE_50_64), PageRequest.of(0, 20));

        assertThat(response.content()).extracting(PolicySummaryResponse::id).containsExactly(overlapping.getId());
    }

    @Test
    void 상한이_구간_시작과_정확히_같으면_겹침으로_처리된다() {
        Policy boundaryMatch = createPolicy(KEYWORD + "-경계값", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(boundaryMatch).minimumAge(20).maximumAge(25).build());
        Policy boundaryMismatch = createPolicy(KEYWORD + "-경계값", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(boundaryMismatch).minimumAge(20).maximumAge(24).build());

        PageResponse<PolicySummaryResponse> response = policyService.searchPolicies(
                new PolicySearchRequest(KEYWORD + "-경계값", null, null, null, AgeGroup.AGE_25_29), PageRequest.of(0, 20));

        assertThat(response.content()).extracting(PolicySummaryResponse::id).containsExactly(boundaryMatch.getId());
    }

    @Test
    void UNDER_19_구간_조회_시_상한만_있는_정책의_포함_여부가_정확히_판정된다() {
        Policy overlapping = createPolicy(KEYWORD + "-19미만", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(overlapping).maximumAge(15).build());
        Policy nonOverlapping = createPolicy(KEYWORD + "-19미만", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(nonOverlapping).minimumAge(20).build());

        PageResponse<PolicySummaryResponse> response = policyService.searchPolicies(
                new PolicySearchRequest(KEYWORD + "-19미만", null, null, null, AgeGroup.UNDER_19), PageRequest.of(0, 20));

        assertThat(response.content()).extracting(PolicySummaryResponse::id).containsExactly(overlapping.getId());
    }

    @Test
    void AGE_65_PLUS_구간_조회_시_하한만_있는_정책의_포함_여부가_정확히_판정된다() {
        Policy overlapping = createPolicy(KEYWORD + "-65이상", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(overlapping).minimumAge(65).build());
        Policy nonOverlapping = createPolicy(KEYWORD + "-65이상", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(nonOverlapping).maximumAge(64).build());

        PageResponse<PolicySummaryResponse> response = policyService.searchPolicies(
                new PolicySearchRequest(KEYWORD + "-65이상", null, null, null, AgeGroup.AGE_65_PLUS), PageRequest.of(0, 20));

        assertThat(response.content()).extracting(PolicySummaryResponse::id).containsExactly(overlapping.getId());
    }

    @Test
    void 상한만_있는_정책도_구간과_겹치면_ageGroup_필터에_포함된다() {
        Policy overlapping = createPolicy(KEYWORD + "-상한만", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(overlapping).maximumAge(30).build());
        Policy nonOverlapping = createPolicy(KEYWORD + "-상한만", PolicyStatus.ALWAYS_OPEN, ApplicationType.ALWAYS, null, null);
        policyEligibilityRepository.save(PolicyEligibility.builder().policy(nonOverlapping).maximumAge(24).build());

        PageResponse<PolicySummaryResponse> response = policyService.searchPolicies(
                new PolicySearchRequest(KEYWORD + "-상한만", null, null, null, AgeGroup.AGE_25_29), PageRequest.of(0, 20));

        assertThat(response.content()).extracting(PolicySummaryResponse::id).containsExactly(overlapping.getId());
    }

    private PolicySummaryResponse findById(PageResponse<PolicySummaryResponse> response, Long policyId) {
        return response.content().stream()
                .filter(summary -> summary.id().equals(policyId))
                .findFirst()
                .orElseThrow();
    }

    private Policy createPolicy(String title, PolicyStatus status, ApplicationType applicationType,
                                 LocalDate startDate, LocalDate endDate) {
        Organization organization = Organization.builder()
                .name("테스트기관")
                .type("중앙부처")
                .build();
        entityManager.persist(organization);

        Policy policy = Policy.builder()
                .organization(organization)
                .title(title)
                .applicationType(applicationType)
                .applicationStartDate(startDate)
                .applicationEndDate(endDate)
                .regionScope(RegionScope.NATIONAL)
                .status(status)
                .build();
        return policyRepository.save(policy);
    }

    private User createUser(String email, String providerUserId) {
        return userRepository.save(User.builder()
                .email(email)
                .provider(OAuthProvider.KAKAO)
                .providerUserId(providerUserId)
                .build());
    }
}
