package com.mozip.server.chat.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatKeywordExpanderTest {

    @Test
    void 원래_키워드_뒤에_동의어를_붙인다() {
        assertThat(ChatKeywordExpander.expand(List.of("대학생")))
                .containsExactly("대학생", "대학", "학자금", "장학");
    }

    @Test
    void 사전에_없는_키워드는_그대로_두고_중복은_제거한다() {
        assertThat(ChatKeywordExpander.expand(List.of("효행장려금", "취준생", "구직자")))
                .containsExactly("효행장려금", "취준생", "구직자", "구직", "취업");
    }

    @Test
    void 빈_목록은_빈_목록을_반환한다() {
        assertThat(ChatKeywordExpander.expand(List.of())).isEmpty();
        assertThat(ChatKeywordExpander.expand(null)).isEmpty();
    }
}
