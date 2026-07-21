package com.mozip.server.policy.entity;

import com.mozip.server.region.entity.Region;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "policy_regions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyRegion {

    @EmbeddedId
    private PolicyRegionId id;

    @MapsId("policyId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @MapsId("regionId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Builder
    public PolicyRegion(Policy policy, Region region) {
        this.policy = policy;
        this.region = region;
        this.id = new PolicyRegionId(policy.getId(), region.getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PolicyRegion that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PolicyRegion{" +
                "id=" + id +
                '}';
    }
}