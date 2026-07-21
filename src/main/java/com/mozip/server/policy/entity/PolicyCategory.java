package com.mozip.server.policy.entity;

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
@Table(name = "policy_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyCategory {

    @EmbeddedId
    private PolicyCategoryId id;

    @MapsId("policyId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @MapsId("categoryId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Builder
    public PolicyCategory(Policy policy, Category category) {
        this.policy = policy;
        this.category = category;
        this.id = new PolicyCategoryId(policy.getId(), category.getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PolicyCategory that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PolicyCategory{" +
                "id=" + id +
                '}';
    }
}