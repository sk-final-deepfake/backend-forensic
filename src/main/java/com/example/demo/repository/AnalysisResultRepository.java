package com.example.demo.repository;

import com.example.demo.domain.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    Optional<AnalysisResult> findByAnalysisRequestId(Long analysisRequestId);
}
