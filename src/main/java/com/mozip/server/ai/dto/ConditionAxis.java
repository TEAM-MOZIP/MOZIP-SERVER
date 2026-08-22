package com.mozip.server.ai.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * MOZIP-AI의 {@code ConditionAxis}(소문자 snake_case, 예: "employment_status")를 그대로
 * 표현하는 enum이다. Java enum 상수명이 이미 대문자 snake_case라 대소문자 변환만으로
 * 정확히 대응된다 — 별도 값별 매핑 테이블이 필요 없다. conditions/extract 응답
 * 역직렬화(소문자 → enum)뿐 아니라, chat/respond 요청의 unresolvedConditions[].axis
 * 직렬화(enum → 소문자)에도 이 타입이 그대로 재사용되므로 양방향 매핑이 모두 필요하다
 * — 기본 Jackson 직렬화(enum {@code name()})는 대문자를 그대로 내보내 AI 쪽 계약과
 * 어긋나므로 {@link JsonValue}로 명시적으로 소문자화한다.
 */
public enum ConditionAxis {
    GENDER,
    AGE,
    REGION,
    EMPLOYMENT_STATUS,
    HOUSEHOLD_TYPE,
    INCOME;

    @JsonCreator
    public static ConditionAxis from(String value) {
        return ConditionAxis.valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
