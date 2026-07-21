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
public class PolicyRegionId implements Serializable {

    private Long policyId;
    private Long regionId;

    public PolicyRegionId(Long policyId, Long regionId) {
        this.policyId = policyId;
        this.regionId = regionId;
    }
}