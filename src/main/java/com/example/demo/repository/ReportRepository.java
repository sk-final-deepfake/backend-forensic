package com.example.demo.repository;

import com.example.demo.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findTopByEvidenceIdOrderByCreatedAtDesc(Long evidenceId);

    List<Report> findByEvidenceIdOrderByCreatedAtDesc(Long evidenceId);
}
