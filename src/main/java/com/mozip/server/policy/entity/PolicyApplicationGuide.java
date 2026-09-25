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

/**
 * AI가 생성한 정책 신청 가이드(단계·준비 서류) 캐시. 정책마다 하나이며, 가이드 원문(신청 절차·준비 서류)이
 * 바뀌면 {@code sourceHash}가 달라져 다시 생성한다. AI 생성에 실패한 원문 대체 결과는 저장하지 않는다.
 */
@Entity
@Table(name = "policy_application_guide")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyApplicationGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false, unique = true)
    private Policy policy;

    /** AI 가이드 응답(steps, requiredDocuments) JSON */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public PolicyApplicationGuide(Policy policy, String content, String sourceHash) {
        this.policy = policy;
        this.content = content;
        this.sourceHash = sourceHash;
    }

    public void update(String content, String sourceHash) {
        this.content = content;
        this.sourceHash = sourceHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PolicyApplicationGuide that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "PolicyApplicationGuide{" +
                "id=" + id +
                ", sourceHash='" + sourceHash + '\'' +
                '}';
    }
}
