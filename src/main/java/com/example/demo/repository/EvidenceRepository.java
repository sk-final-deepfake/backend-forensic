package com.example.demo.repository;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface EvidenceRepository extends JpaRepository<Evidence, Long>, JpaSpecificationExecutor<Evidence> {

    long countByFileTypeAndDeletedAtIsNull(FileType fileType);

    List<Evidence> findByUploaderIdAndStatusAndDeletedAtIsNullOrderByUploadedAtDesc(
            Long uploaderId,
            EvidenceStatus status
    );

    Optional<Evidence> findByEvidenceId(Long evidenceId);
}
