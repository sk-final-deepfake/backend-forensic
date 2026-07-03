package com.example.demo.repository;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.EvidenceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EvidenceRepository extends JpaRepository<Evidence, Long>, JpaSpecificationExecutor<Evidence> {

    List<Evidence> findByUploaderIdAndStatusAndDeletedAtIsNullOrderByUploadedAtDesc(
            Long uploaderId,
            EvidenceStatus status
    );

    Optional<Evidence> findByEvidenceId(Long evidenceId);

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

    @Query("""
            SELECT e
            FROM Evidence e
            WHERE e.uploaderId = :uploaderId
              AND e.deletedAt IS NULL
              AND (
                :search IS NULL OR TRIM(:search) = '' OR
                LOWER(e.fileName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                LOWER(COALESCE(e.caseName, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR
                LOWER(COALESCE(e.caseNumber, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            ORDER BY e.uploadedAt DESC
            """)
    Page<Evidence> findCompareOriginals(
            @Param("uploaderId") Long uploaderId,
            @Param("search") String search,
            Pageable pageable
    );
}
