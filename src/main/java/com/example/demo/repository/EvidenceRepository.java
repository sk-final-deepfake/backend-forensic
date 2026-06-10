package com.example.demo.repository;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

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
}
