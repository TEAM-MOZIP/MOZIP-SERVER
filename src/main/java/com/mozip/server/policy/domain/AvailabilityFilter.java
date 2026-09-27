package com.mozip.server.policy.domain;

/**
 * 정책 목록의 "상태" 필터. DB에 저장된 PolicyStatus가 아니라, 오늘 날짜 기준으로
 * {@code PolicyAvailabilityEvaluator}가 계산하는 신청 가능 상태와 같은 기준으로 거른다.
 */
public enum AvailabilityFilter {
    /** 접수 중: 상시 신청이거나 오늘이 신청기간 안인 정책(마감 임박 포함) */
    OPEN,
    /** 마감 임박: 신청기간 안이면서 마감까지 CLOSING_SOON_THRESHOLD_DAYS일 이내 */
    CLOSING_SOON,
    /** 예정: 신청 시작일이 오늘 이후 */
    UPCOMING
}
