package com.mozip.server.chat.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatKeywordExtractorTest {

    @Test
    void 주제_키워드만_추출하고_인사_나이_성별_범용어는_제외한다() {
        assertThat(ChatKeywordExtractor.extract("안녕 나는 스무살 여자야. 청년 지원 정책 알려줘"))
                .containsExactly("청년");
    }

    @Test
    void 조사와_어미를_떼어낸다() {
        assertThat(ChatKeywordExtractor.extract("월세 관련해서")).containsExactly("월세");
        assertThat(ChatKeywordExtractor.extract("20대 취준생인데 교육 받고 싶어")).containsExactly("취준생", "교육");
        assertThat(ChatKeywordExtractor.extract("대학생 장학금 추천해줘")).containsExactly("대학생", "장학금");
    }

    @Test
    void 명사_끝_글자와_겹치는_조사는_떼지_않는다() {
        assertThat(ChatKeywordExtractor.extract("국민취업지원제도가 뭐야?")).containsExactly("국민취업지원제도");
    }

    @Test
    void 특정_정책_후속_질문에는_키워드가_없다() {
        assertThat(ChatKeywordExtractor.extract("신청 기간은?")).isEmpty();
        assertThat(ChatKeywordExtractor.extract("그거 서류는 뭐 필요해?")).isEmpty();
        assertThat(ChatKeywordExtractor.extract("두 번째 정책은 뭐야?")).isEmpty();
    }

    @Test
    void 조건_축으로_처리되는_나이_지역_성별_표현은_키워드가_아니다() {
        assertThat(ChatKeywordExtractor.extract("서울 사는 25살인데 받을 정책 있어?")).isEmpty();
        assertThat(ChatKeywordExtractor.extract("나 20살이야")).isEmpty();
        assertThat(ChatKeywordExtractor.extract("저는 여성입니다.")).isEmpty();
    }

    @Test
    void 중복을_제거하고_최대_5개까지만_반환한다() {
        assertThat(ChatKeywordExtractor.extract("월세 월세를 청년 주거 교육 장학금 돌봄 문화"))
                .containsExactly("월세", "청년", "주거", "교육", "장학금");
    }

    @Test
    void 지급_안내처럼_정책_제목에_흔한_단어는_키워드가_아니다() {
        assertThat(ChatKeywordExtractor.extract("효행장려금 지급 정책에 대해서 알려줘")).containsExactly("효행장려금");
    }

    @Test
    void 위한_대한처럼_문장을_잇는_말은_키워드가_아니다() {
        assertThat(ChatKeywordExtractor.extract("20대 여성을 위한 지원 정책")).isEmpty();
        assertThat(ChatKeywordExtractor.extract("대학생에게 도움이 될 만한 주거 정책")).containsExactly("대학생", "주거");
    }

    @Test
    void 빈_메시지는_빈_목록을_반환한다() {
        assertThat(ChatKeywordExtractor.extract(null)).isEmpty();
        assertThat(ChatKeywordExtractor.extract("  ")).isEmpty();
    }
}
