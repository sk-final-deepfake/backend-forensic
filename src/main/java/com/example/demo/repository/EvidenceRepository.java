package com.example.demo.repository;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.EvidenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

	List<Evidence> findByUploaderIdAndStatusOrderByUploadedAtDesc(Long uploaderId, EvidenceStatus status);
}
