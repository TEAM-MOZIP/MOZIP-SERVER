package com.mozip.server.policy.entity;

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
@Table(name = "policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 300)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_description", columnDefinition = "TEXT")
    private String targetDescription;

    @Column(name = "benefit_description", columnDefinition = "TEXT")
    private String benefitDescription;

    @Column(name = "application_method", columnDefinition = "TEXT")
    private String applicationMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_type", nullable = false, length = 20)
    private ApplicationType applicationType;

    @Column(name = "application_start_date")
    private LocalDate applicationStartDate;

    @Column(name = "application_end_date")
    private LocalDate applicationEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "region_scope", nullable = false, length = 20)
    private RegionScope regionScope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "source_updated_at")
    private LocalDateTime sourceUpdatedAt;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Policy(Organization organization, String title, String summary, String description,
                  String targetDescription, String benefitDescription, String applicationMethod,
                  ApplicationType applicationType, LocalDate applicationStartDate, LocalDate applicationEndDate,
                  RegionScope regionScope, PolicyStatus status, String sourceUrl,
                  LocalDateTime sourceUpdatedAt, LocalDateTime lastVerifiedAt) {
        this.organization = organization;
        this.title = title;
        this.summary = summary;
        this.description = description;
        this.targetDescription = targetDescription;
        this.benefitDescription = benefitDescription;
        this.applicationMethod = applicationMethod;
        this.applicationType = applicationType;
        this.applicationStartDate = applicationStartDate;
        this.applicationEndDate = applicationEndDate;
        this.regionScope = regionScope;
        this.status = status;
        this.sourceUrl = sourceUrl;
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.lastVerifiedAt = lastVerifiedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Policy policy)) return false;
        return id != null && id.equals(policy.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Policy{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", status=" + status +
                '}';
    }
}