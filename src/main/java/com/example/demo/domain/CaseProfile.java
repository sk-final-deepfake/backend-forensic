package com.example.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "case_profiles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"uploader_id", "case_key"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_profile_id")
    private Long caseProfileId;

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "case_key", nullable = false, length = 255)
    private String caseKey;

    @Column(name = "representative_evidence_id")
    private Long representativeEvidenceId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public CaseProfile(Long uploaderId, String caseKey, Long representativeEvidenceId) {
        this.uploaderId = uploaderId;
        this.caseKey = caseKey;
        this.representativeEvidenceId = representativeEvidenceId;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateRepresentativeEvidence(Long representativeEvidenceId) {
        this.representativeEvidenceId = representativeEvidenceId;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateCaseKey(String caseKey) {
        this.caseKey = caseKey;
        this.updatedAt = LocalDateTime.now();
    }
}
