package com.mozip.server.chat.evaluator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 챗봇 메시지에서 정책 탐색용 주제 키워드("월세", "청년", "주거" 등)를 뽑는다.
 *
 * <p>형태소 분석기 없이 공백·기호로 토큰을 나누고, 흔한 조사/어미를 떼어낸 뒤 불용어를 제거하는
 * 규칙 기반 방식이다. 나이·성별·지역처럼 {@code conditions/extract}가 조건 축으로 따로 처리하는 표현과,
 * "신청 기간은?"처럼 특정 정책에 대한 후속 질문에 쓰이는 단어(신청·기간·서류 등)는 키워드로 보지 않는다 —
 * 후속 질문이 키워드 탐색으로 빠지지 않고 기존처럼 history 기반 AI 응답으로 처리되게 하기 위함이다.
 */
public final class ChatKeywordExtractor {

    private static final int MAX_KEYWORDS = 5;
    private static final int MIN_KEYWORD_LENGTH = 2;

    private static final Pattern TOKEN_DELIMITER = Pattern.compile("[^0-9a-z가-힣]+");
    private static final Pattern AGE_TOKEN = Pattern.compile("^\\d+(살|세|대)?$|^.+살$");

    /**
     * 떼어낼 조사/어미. 길이가 긴 것부터 검사해야 "에서"가 "서"보다 먼저 떨어진다. 한 번만 떼어낸다.
     * "도·만·과·의·로"처럼 명사 끝 글자와 겹치기 쉬운 조사(제도, 효과, 진로 등)는 넣지 않는다.
     */
    private static final List<String> SUFFIXES = List.of(
            "이에요", "입니다", "에서는", "으로는", "이라고", "인데요",
            "예요", "이야", "이요", "인데", "에서", "으로", "에게", "한테", "까지", "부터", "이랑", "하고", "처럼", "이나",
            "라고", "은", "는", "이", "가", "을", "를", "에", "와", "랑", "야", "요");

    private static final List<String> STOPWORD_PREFIXES = List.of(
            "알려", "추천", "찾아", "궁금", "있", "없", "받을", "받고", "받는", "하고싶", "싶", "해줘", "해주", "필요");

    private static final Set<String> STOPWORDS = Set.of(
            // 인사·지시어·대명사
            "안녕", "안녕하세요", "혹시", "그냥", "진짜", "정말", "사실", "근데", "그럼", "그리고", "사람", "좀", "뭐", "뭐야", "뭐가", "무엇", "어떤", "어떻게",
            "이거", "저거", "그거", "이것", "그것", "우리", "저희", "나는", "저는", "제가", "내가", "너는",
            // 문장을 잇는 말("~을 위한", "~에 대한")은 정책 제목·요약에도 흔해서 엉뚱한 정책을 끌어온다
            "위한", "위해", "대한", "관한", "통한", "따른", "같은", "필요한", "좋은", "받을만한", "도움", "도움이",
            "도움될", "만한", "해당", "어떤게", "무엇이",
            // 범용 도메인 단어(거의 모든 정책에 등장해 변별력이 없음)
            "정책", "지원", "지원금", "혜택", "제도", "사업", "서비스", "관련", "관련해서", "관련된", "대해", "대해서",
            "종류", "목록", "정보", "지급", "지원사업", "안내", "운영", "제공", "사업안내",
            // 특정 정책 후속 질문에 쓰이는 단어
            "신청", "기간", "방법", "서류", "구비서류", "대상", "조건", "자격", "금액", "언제", "어디", "얼마", "가능",
            "가능해", "마감", "절차", "번째", "다음", "이전",
            // 조건 축(conditions/extract)이 따로 다루는 표현
            "여자", "남자", "여성", "남성", "서울", "서울시", "서울특별시", "사는", "살아", "살고", "거주");

    private ChatKeywordExtractor() {
    }

    public static List<String> extract(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        Set<String> keywords = new LinkedHashSet<>();
        for (String rawToken : TOKEN_DELIMITER.split(message.toLowerCase(Locale.ROOT))) {
            String token = stripSuffix(rawToken);
            if (isKeyword(token)) {
                keywords.add(token);
            }
            if (keywords.size() == MAX_KEYWORDS) {
                break;
            }
        }
        return new ArrayList<>(keywords);
    }

    private static String stripSuffix(String token) {
        if (STOPWORDS.contains(token)) {
            return token;
        }
        for (String suffix : SUFFIXES) {
            if (token.endsWith(suffix) && token.length() - suffix.length() >= MIN_KEYWORD_LENGTH) {
                return token.substring(0, token.length() - suffix.length());
            }
        }
        return token;
    }

    private static boolean isKeyword(String token) {
        if (token.length() < MIN_KEYWORD_LENGTH || STOPWORDS.contains(token) || AGE_TOKEN.matcher(token).matches()) {
            return false;
        }
        return STOPWORD_PREFIXES.stream().noneMatch(token::startsWith);
    }
}
