package com.mozip.server.policy.domain;

import lombok.Getter;

@Getter
public enum AgeGroup {

    UNDER_19(null, 18),
    AGE_19_24(19, 24),
    AGE_25_29(25, 29),
    AGE_30_34(30, 34),
    AGE_35_49(35, 49),
    AGE_50_64(50, 64),
    AGE_65_PLUS(65, null);

    private final Integer minAge;
    private final Integer maxAge;

    AgeGroup(Integer minAge, Integer maxAge) {
        this.minAge = minAge;
        this.maxAge = maxAge;
    }
}
