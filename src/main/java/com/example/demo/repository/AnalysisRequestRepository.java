package com.example.demo.repository;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnalysisRequestRepository extends JpaRepository<AnalysisRequest, Long> {

    List<AnalysisRequest> findByEvidenceIdInOrderByRequestedAtDesc(List<Long> evidenceIds);

    List<AnalysisRequest> findByEvidenceIdOrderByRequestedAtDesc(Long evidenceId);

    boolean existsByEvidenceId(Long evidenceId);

    Optional<AnalysisRequest> findTopByEvidenceIdOrderByRequestedAtDesc(Long evidenceId);

    void deleteByEvidenceId(Long evidenceId);

    @Query("""
            SELECT COUNT(ar)
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            WHERE ar.requestedBy = :uploaderId
              AND e.deletedAt IS NULL
            """)
    long countTotalByUploader(@Param("uploaderId") Long uploaderId);

    @Query("""
            SELECT COUNT(ar)
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            WHERE ar.requestedBy = :uploaderId
              AND e.deletedAt IS NULL
              AND ar.status = :status
            """)
    long countByUploaderAndStatus(
            @Param("uploaderId") Long uploaderId,
            @Param("status") AnalysisStatus status
    );

    @Query("""
            SELECT COUNT(ar)
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            WHERE ar.requestedBy = :uploaderId
              AND e.deletedAt IS NULL
              AND ar.status IN :statuses
            """)
    long countByUploaderAndStatusIn(
            @Param("uploaderId") Long uploaderId,
            @Param("statuses") List<AnalysisStatus> statuses
    );

    @Query("""
            SELECT COUNT(ar)
            FROM AnalysisRequest ar
            JOIN Evidence e ON e.evidenceId = ar.evidenceId
            JOIN AnalysisResult r ON r.analysisRequestId = ar.analysisRequestId
            WHERE ar.requestedBy = :uploaderId
              AND e.deletedAt IS NULL
              AND ar.status = com.example.demo.domain.enums.AnalysisStatus.COMPLETED
              AND r.riskLevel IN (
                  com.example.demo.domain.enums.RiskLevel.HIGH,
                  com.example.demo.domain.enums.RiskLevel.MEDIUM
              )
            """)
    long countDeepfakeDetectedByUploader(@Param("uploaderId") Long uploaderId);

    @Query("""
            SELECT COUNT(DISTINCT e.evidenceId)
            FROM Evidence e
            WHERE e.deletedAt IS NULL
              AND e.fileType = :fileType
              AND EXISTS (
                  SELECT 1
                  FROM AnalysisRequest ar
                  WHERE ar.evidenceId = e.evidenceId
                    AND ar.status = :status
              )
            """)
    long countCompletedAnalysesByFileType(
            @Param("fileType") FileType fileType,
            @Param("status") AnalysisStatus status
    );

    @Query("""
            SELECT COUNT(e)
            FROM Evidence e
            WHERE e.deletedAt IS NULL
              AND e.fileType = :fileType
              AND EXISTS (
                  SELECT 1
                  FROM AnalysisRequest ar
                  WHERE ar.evidenceId = e.evidenceId
              )
            """)
    long countByFileTypeWithAnalysisRequest(@Param("fileType") FileType fileType);

    @Query("""
            SELECT COUNT(e)
            FROM Evidence e
            WHERE e.deletedAt IS NULL
              AND e.fileType = :fileType
              AND e.uploaderId = :uploaderId
              AND EXISTS (
                  SELECT 1
                  FROM AnalysisRequest ar
                  WHERE ar.evidenceId = e.evidenceId
                    AND ar.requestedBy = :uploaderId
              )
            """)
    long countByFileTypeAndUploaderWithAnalysisRequest(
            @Param("fileType") FileType fileType,
            @Param("uploaderId") Long uploaderId
    );
}
