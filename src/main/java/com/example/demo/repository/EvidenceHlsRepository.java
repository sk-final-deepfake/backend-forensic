package com.example.demo.repository;

import com.example.demo.domain.EvidenceHls;
import com.example.demo.domain.enums.HlsStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceHlsRepository extends JpaRepository<EvidenceHls, Long> {

    Optional<EvidenceHls> findByEvidenceId(Long evidenceId);

    List<EvidenceHls> findByEvidenceIdIn(Collection<Long> evidenceIds);

    boolean existsByEvidenceId(Long evidenceId);

    /**
     * 백필·재패키징 대상 evidence ID (페이지 단위). Evidence 엔티티 N건 로드 없음.
     */
    @Query("""
            SELECT e.evidenceId
            FROM Evidence e
            LEFT JOIN EvidenceHls h ON h.evidenceId = e.evidenceId
            WHERE e.fileType = com.example.demo.domain.enums.FileType.VIDEO
              AND e.lifecycleStatus = com.example.demo.domain.enums.EvidenceLifecycleStatus.ACTIVE
              AND e.deletedAt IS NULL
              AND (h IS NULL OR h.hlsStatus IN :retryStatuses)
            ORDER BY e.evidenceId ASC
            """)
    List<Long> findEvidenceIdsNeedingHlsPackaging(
            @Param("retryStatuses") Collection<HlsStatus> retryStatuses,
            Pageable pageable
    );

    @Query("""
            SELECT h
            FROM EvidenceHls h
            WHERE h.hlsStatus = com.example.demo.domain.enums.HlsStatus.PACKAGING
              AND h.updatedAt < :staleBefore
            """)
    List<EvidenceHls> findStalePackagingRows(@Param("staleBefore") java.time.LocalDateTime staleBefore);
}
