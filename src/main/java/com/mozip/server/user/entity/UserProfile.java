package com.mozip.server.user.entity;

import com.mozip.server.region.entity.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "income_type", length = 20)
    private IncomeType incomeType;

    @Column(name = "income_value")
    private Integer incomeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", length = 30)
    private EmploymentStatus employmentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "household_type", length = 30)
    private HouseholdType householdType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public UserProfile(User user, LocalDate birthDate, Region region, Gender gender, IncomeType incomeType,
                        Integer incomeValue, EmploymentStatus employmentStatus, HouseholdType householdType) {
        this.user = user;
        this.birthDate = birthDate;
        this.region = region;
        this.gender = gender;
        this.incomeType = incomeType;
        this.incomeValue = incomeValue;
        this.employmentStatus = employmentStatus;
        this.householdType = householdType;
    }

    public void update(LocalDate birthDate, Region region, Gender gender, IncomeType incomeType,
                        Integer incomeValue, EmploymentStatus employmentStatus, HouseholdType householdType) {
        this.birthDate = birthDate;
        this.region = region;
        this.gender = gender;
        this.incomeType = incomeType;
        this.incomeValue = incomeValue;
        this.employmentStatus = employmentStatus;
        this.householdType = householdType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProfile that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "UserProfile{" +
                "id=" + id +
                ", birthDate=" + birthDate +
                '}';
    }
}
