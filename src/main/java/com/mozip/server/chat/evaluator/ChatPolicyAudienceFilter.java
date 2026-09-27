package com.mozip.server.chat.evaluator;

import com.mozip.server.policy.entity.Policy;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 정책 제목에 특정 대상 집단(어업인·군인·간호사·교직원·노인 등)이 적혀 있는데 사용자가 그 집단을 언급한 적이
 * 없으면 추천 후보에서 뺀다. 이런 정책은 DB에 나이·성별·지역 제한이 없어 자격 평가를 그대로 통과하기 때문에,
 * "교육" 같은 넓은 키워드로 찾으면 "어업인안전조업교육지원"처럼 사용자와 무관한 정책이 섞인다.
 * 제목만 본다 — 지원대상 본문에는 "장애인 우대" 같은 부가 문구가 흔해 과하게 걸러진다.
 */
public final class ChatPolicyAudienceFilter {

    /**
     * @param titleWords 정책 제목에 있으면 이 집단 전용으로 보는 단어
     * @param userWords  사용자 대화에 있으면 이 집단 정책을 허용하는 단어(titleWords 포함)
     */
    private record AudienceGroup(List<String> titleWords, List<String> userWords) {
        static AudienceGroup of(List<String> titleWords, List<String> extraUserWords) {
            return new AudienceGroup(titleWords,
                    java.util.stream.Stream.concat(titleWords.stream(), extraUserWords.stream()).toList());
        }
    }

    private static final List<AudienceGroup> GROUPS = List.of(
            AudienceGroup.of(List.of("어업인", "어업", "어선", "어민", "농업인", "농업", "농가", "농민", "농어민",
                    "농어업", "임업", "축산", "귀농", "귀어"), List.of("농사", "어촌", "농촌")),
            AudienceGroup.of(List.of("군인", "장병", "전역", "제대군인", "병사", "예비군", "군복무"), List.of("군대", "입대")),
            AudienceGroup.of(List.of("보훈", "국가유공자", "유공자", "참전"), List.of()),
            AudienceGroup.of(List.of("간호사", "의료인", "요양보호사"), List.of("간호", "의사")),
            AudienceGroup.of(List.of("교직원", "교원", "교사", "강사"), List.of("선생님")),
            AudienceGroup.of(List.of("종사자"), List.of("일하는", "근무")),
            AudienceGroup.of(List.of("소상공인", "자영업", "사업주", "사업자"), List.of("가게", "장사", "창업")),
            AudienceGroup.of(List.of("노인", "어르신", "고령", "경로", "치매"), List.of("할머니", "할아버지", "부모님")),
            AudienceGroup.of(List.of("장애인", "장애"), List.of()),
            AudienceGroup.of(List.of("임산부", "임신", "산모", "출산", "난임"), List.of("아기", "출산")),
            AudienceGroup.of(List.of("아동", "영유아", "유아", "어린이", "초등", "보육"),
                    List.of("아이", "아기", "자녀", "육아", "애기")),
            AudienceGroup.of(List.of("청소년", "중학생", "고등학생", "학교밖"), List.of("자녀", "고등학교", "중학교")),
            AudienceGroup.of(List.of("한부모", "다문화", "북한이탈", "새터민", "외국인", "결혼이민"), List.of()));

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ChatPolicyAudienceFilter() {
    }

    /** 사용자 대화({@code userText})에 비추어 이 정책을 추천 후보로 남겨도 되는지. */
    public static boolean isForUser(Policy policy, String userText) {
        String title = normalize(policy.getTitle());
        String user = normalize(userText);
        return GROUPS.stream()
                .filter(group -> group.titleWords().stream().anyMatch(title::contains))
                .allMatch(group -> group.userWords().stream().anyMatch(user::contains));
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
