package com.mozip.server.policy.domain;

import static com.mozip.server.policy.domain.PolicyPackageSection.TRANSPORT_KEYWORDS;
import static com.mozip.server.policy.domain.PolicyPackageSection.category;
import static com.mozip.server.policy.domain.PolicyPackageSection.categoryAndKeyword;
import static com.mozip.server.policy.domain.PolicyPackageSection.categoryOrKeyword;
import static com.mozip.server.policy.domain.PolicyPackageSection.keyword;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 대상자별 정책 패키지 정의. 패키지 id는 클라이언트의 패키지 id와 같다(제목·문구 등 표시 정보는 클라이언트가 관리).
 *
 * <p>정책은 다음을 모두 만족하면 패키지에 들어간다({@link PolicyPackageSelector}).
 * <ul>
 *   <li>나이 제한이 패키지 나이 범위와 비슷하다(±5세). 나이 제한이 없거나 넓으면 제목·요약·지원대상에
 *       {@code targetTerms} 중 하나가 포함된다({@code excludedTargetTerms}는 먼저 지운 뒤 비교).
 *       나이 제한이 패키지 나이와 아예 안 겹치면 제외한다.</li>
 *   <li>섹션 중 하나 이상에 해당한다.</li>
 * </ul>
 *
 * @param sections 화면 표시 순서. 배정은 키워드가 있는 섹션을 먼저 본다({@link #assignmentOrder()}).
 */
public enum PolicyPackage {

    JOB_SEEKER("job-seeker", 19, 39,
            List.of("청년", "구직", "취업준비", "대학생"),
            List.of(),
            List.of(
                    category("employment", "취업", "EMPLOYMENT"),
                    category("education", "교육", "EDUCATION"),
                    category("startup", "창업", "STARTUP").shared()
            )),

    SOLO_YOUTH("solo-youth", 19, 39,
            List.of("청년", "신혼", "1인가구"),
            List.of(),
            List.of(
                    categoryAndKeyword("rent", "월세·전세", List.of("월세", "전세", "임차", "보증금"), "HOUSING"),
                    category("housing", "주거", "HOUSING")
            )),

    SENIOR("senior", 50, null,
            List.of("노인", "어르신", "고령", "중장년", "경로", "은퇴"),
            List.of(),
            List.of(
                    category("welfare", "복지", "WELFARE"),
                    keyword("transport", "교통", TRANSPORT_KEYWORDS),
                    category("culture", "문화", "CULTURE"),
                    categoryOrKeyword("retirement", "노후", List.of("연금", "노후", "퇴직"), "EMPLOYMENT")
            )),

    TEEN("teen", 9, 18,
            List.of("청소년", "학생", "학교밖"),
            // "학생"이 "대학생"에 걸리지 않게 먼저 지운다.
            List.of("대학생", "대학원생"),
            List.of(
                    category("education", "교육", "EDUCATION"),
                    keyword("transport", "교통", TRANSPORT_KEYWORDS),
                    category("culture", "문화", "CULTURE")
            ));

    private final String id;
    private final Integer minimumAge;
    private final Integer maximumAge;
    private final List<String> targetTerms;
    private final List<String> excludedTargetTerms;
    private final List<PolicyPackageSection> sections;

    PolicyPackage(String id, Integer minimumAge, Integer maximumAge, List<String> targetTerms,
                  List<String> excludedTargetTerms, List<PolicyPackageSection> sections) {
        this.id = id;
        this.minimumAge = minimumAge;
        this.maximumAge = maximumAge;
        this.targetTerms = targetTerms;
        this.excludedTargetTerms = excludedTargetTerms;
        this.sections = sections;
    }

    public static Optional<PolicyPackage> fromId(String id) {
        return Arrays.stream(values()).filter(policyPackage -> policyPackage.id.equals(id)).findFirst();
    }

    public Optional<PolicyPackageSection> findSection(String sectionKey) {
        return sections.stream().filter(section -> section.key().equals(sectionKey)).findFirst();
    }

    /** 섹션 배정 순서: 키워드가 있는 섹션(교통, 월세·전세, 노후)을 먼저, 나머지는 표시 순서대로. */
    public List<PolicyPackageSection> assignmentOrder() {
        return sections.stream()
                .sorted(Comparator.comparing(section -> section.hasKeywords() ? 0 : 1))
                .toList();
    }

    public String getId() {
        return id;
    }

    /** null이면 하한 없음 */
    public Integer getMinimumAge() {
        return minimumAge;
    }

    /** null이면 상한 없음 */
    public Integer getMaximumAge() {
        return maximumAge;
    }

    public List<String> getTargetTerms() {
        return targetTerms;
    }

    public List<String> getExcludedTargetTerms() {
        return excludedTargetTerms;
    }

    public List<PolicyPackageSection> getSections() {
        return sections;
    }
}
