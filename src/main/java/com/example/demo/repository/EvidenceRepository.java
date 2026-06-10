package com.example.demo.repository;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.FileType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    long countByFileTypeAndDeletedAtIsNull(FileType fileType);
}
