package com.mozip.server.policy.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyCategoryId implements Serializable {

    private Long policyId;
    private Long categoryId;

    public PolicyCategoryId(Long policyId, Long categoryId) {
        this.policyId = policyId;
        this.categoryId = categoryId;
    }
}