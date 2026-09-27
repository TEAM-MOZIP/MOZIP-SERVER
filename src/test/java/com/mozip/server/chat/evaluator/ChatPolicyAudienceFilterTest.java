package com.mozip.server.chat.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import com.mozip.server.policy.entity.ApplicationType;
import com.mozip.server.policy.entity.Policy;
import com.mozip.server.policy.entity.PolicyStatus;
import com.mozip.server.policy.entity.RegionScope;
import org.junit.jupiter.api.Test;

class ChatPolicyAudienceFilterTest {

    @Test
    void 제목에_대상_집단이_없으면_통과한다() {
        assertThat(ChatPolicyAudienceFilter.isForUser(policy("취업 후 상환 학자금대출"), "25살 대학생")).isTrue();
    }

    @Test
    void 사용자가_언급하지_않은_대상_집단_정책은_걸러낸다() {
        String userText = "양천구에 사는 25살 여자 대학생\n교육 관련해서 궁금해";

        assertThat(ChatPolicyAudienceFilter.isForUser(policy("어업인안전조업교육지원"), userText)).isFalse();
        assertThat(ChatPolicyAudienceFilter.isForUser(policy("유휴간호사 등 교육 및 취업 연계 서비스 제공"), userText)).isFalse();
        assertThat(ChatPolicyAudienceFilter.isForUser(policy("사립학교 교직원 국고 학자금 대여"), userText)).isFalse();
        assertThat(ChatPolicyAudienceFilter.isForUser(policy("청소년 권리교육 강사매칭시스템 및 이러닝 교육"), userText)).isFalse();
    }

    @Test
    void 사용자가_해당_집단을_언급했으면_통과한다() {
        assertThat(ChatPolicyAudienceFilter.isForUser(policy("노인 일자리 지원"), "어르신 일자리 알려줘")).isTrue();
        assertThat(ChatPolicyAudienceFilter.isForUser(policy("영유아 보육료 지원"), "아이 키우는데 지원 있어?")).isTrue();
    }

    private Policy policy(String title) {
        return Policy.builder()
                .title(title)
                .applicationType(ApplicationType.ALWAYS)
                .regionScope(RegionScope.NATIONAL)
                .status(PolicyStatus.OPEN)
                .build();
    }
}
