package com.example.demo.repository;

import com.example.demo.domain.AnalysisRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisRequestRepository extends JpaRepository<AnalysisRequest, Long> {

	List<AnalysisRequest> findByEvidenceIdInOrderByRequestedAtDesc(List<Long> evidenceIds);
}
