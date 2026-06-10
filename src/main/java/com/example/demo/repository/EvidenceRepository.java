package com.example.demo.repository;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    long countByFileTypeAndDeletedAtIsNull(FileType fileType);

    List<Evidence> findByUploaderIdAndStatusAndDeletedAtIsNullOrderByUploadedAtDesc(
            Long uploaderId,
            EvidenceStatus status
    );

    List<Evidence> findByEvidenceIdInAndUploaderIdAndDeletedAtIsNull(
            List<Long> evidenceIds,
            Long uploaderId
    );

    Optional<Evidence> findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(
            Long evidenceId,
            Long uploaderId
    );

    @Query("""
            SELECT e
            FROM Evidence e
            WHERE e.uploaderId = :uploaderId
              AND e.deletedAt IS NULL
              AND (e.caseNumber = :caseKey OR e.caseName = :caseKey)
            ORDER BY e.uploadedAt DESC
            """)
    List<Evidence> findByUploaderIdAndCaseKey(
            @Param("uploaderId") Long uploaderId,
            @Param("caseKey") String caseKey
    );
}
