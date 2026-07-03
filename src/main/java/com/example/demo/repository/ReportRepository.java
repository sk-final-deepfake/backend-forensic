package com.example.demo.repository;

import com.example.demo.domain.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findTopByEvidenceIdOrderByCreatedAtDesc(Long evidenceId);

    List<Report> findByEvidenceIdOrderByCreatedAtDesc(Long evidenceId);

    Optional<Report> findTopByCompareIdOrderByCreatedAtDesc(Long compareId);

    Optional<Report> findByEvidenceIdAndReportHash(Long evidenceId, String reportHash);

    Page<Report> findByCreatedByOrderByCreatedAtDesc(Long createdBy, Pageable pageable);
}
