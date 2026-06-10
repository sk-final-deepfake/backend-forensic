package com.example.demo.repository;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnalysisRequestRepository extends JpaRepository<AnalysisRequest, Long> {

    List<AnalysisRequest> findByEvidenceIdInOrderByRequestedAtDesc(List<Long> evidenceIds);

    boolean existsByEvidenceId(Long evidenceId);

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
