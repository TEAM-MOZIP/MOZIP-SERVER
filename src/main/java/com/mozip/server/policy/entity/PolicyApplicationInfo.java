package com.mozip.server.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "policy_application_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyApplicationInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false, unique = true)
    private Policy policy;

    @Column(name = "application_procedure", columnDefinition = "TEXT")
    private String applicationProcedure;

    @Column(name = "required_documents_text", columnDefinition = "TEXT")
    private String requiredDocumentsText;

    @Column(name = "application_url", length = 500)
    private String applicationUrl;

    @Column(name = "contact_info", columnDefinition = "TEXT")
    private String contactInfo;

    @Column(name = "application_notes", columnDefinition = "TEXT")
    private String applicationNotes;

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public PolicyApplicationInfo(Policy policy, String applicationProcedure, String requiredDocumentsText,
                                  String applicationUrl, String contactInfo, String applicationNotes,
                                  String sourceUrl, LocalDateTime verifiedAt) {
        this.policy = policy;
        this.applicationProcedure = applicationProcedure;
        this.requiredDocumentsText = requiredDocumentsText;
        this.applicationUrl = applicationUrl;
        this.contactInfo = contactInfo;
        this.applicationNotes = applicationNotes;
        this.sourceUrl = sourceUrl;
        this.verifiedAt = verifiedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PolicyApplicationInfo that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PolicyApplicationInfo{" +
                "id=" + id +
                ", sourceUrl='" + sourceUrl + '\'' +
                ", verifiedAt=" + verifiedAt +
                '}';
    }
}
