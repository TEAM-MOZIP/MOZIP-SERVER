package com.mozip.server.policy.entity;

import com.mozip.server.user.entity.IncomeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "policy_eligibility")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyEligibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false, unique = true)
    private Policy policy;

    @Column(name = "minimum_age")
    private Integer minimumAge;

    @Column(name = "maximum_age")
    private Integer maximumAge;

    @Column(name = "gender_condition", length = 20)
    private String genderCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "income_type", length = 20)
    private IncomeType incomeType;

    @Column(name = "minimum_income_value")
    private Integer minimumIncomeValue;

    @Column(name = "maximum_income_value")
    private Integer maximumIncomeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_employment_statuses", columnDefinition = "jsonb")
    private List<String> allowedEmploymentStatuses;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_household_types", columnDefinition = "jsonb")
    private List<String> allowedHouseholdTypes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_conditions", columnDefinition = "jsonb")
    private Map<String, Object> additionalConditions;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public PolicyEligibility(Policy policy, Integer minimumAge, Integer maximumAge, String genderCondition,
                              IncomeType incomeType, Integer minimumIncomeValue, Integer maximumIncomeValue,
                              List<String> allowedEmploymentStatuses, List<String> allowedHouseholdTypes,
                              Map<String, Object> additionalConditions) {
        this.policy = policy;
        this.minimumAge = minimumAge;
        this.maximumAge = maximumAge;
        this.genderCondition = genderCondition;
        this.incomeType = incomeType;
        this.minimumIncomeValue = minimumIncomeValue;
        this.maximumIncomeValue = maximumIncomeValue;
        this.allowedEmploymentStatuses = allowedEmploymentStatuses;
        this.allowedHouseholdTypes = allowedHouseholdTypes;
        this.additionalConditions = additionalConditions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PolicyEligibility that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PolicyEligibility{" +
                "id=" + id +
                ", minimumAge=" + minimumAge +
                ", maximumAge=" + maximumAge +
                '}';
    }
}