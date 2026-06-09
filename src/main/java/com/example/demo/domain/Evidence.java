package com.example.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evidence {

	public static final String HASH_ALGORITHM_SHA256 = "SHA-256";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "evidence_id")
	private Long evidenceId;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Column(name = "case_name")
	private String caseName;

	@Column(name = "hash_algorithm", nullable = false, length = 20)
	private String hashAlgorithm;

	@Column(name = "hash_value", nullable = false, length = 64)
	private String hashValue;

	@Column(name = "original_storage_path", nullable = false)
	private String originalStoragePath;

	@Column(name = "uploaded_at", nullable = false)
	private LocalDateTime uploadedAt;

	@Builder
	public Evidence(
	                String fileName,
	                String caseName,
	                String hashAlgorithm,
	                String hashValue,
	                String originalStoragePath,
	                LocalDateTime uploadedAt
	) {
	        this.fileName = fileName;
	        this.caseName = caseName;
	        this.hashAlgorithm = hashAlgorithm;
	        this.hashValue = hashValue;
	        this.originalStoragePath = originalStoragePath;
	        this.uploadedAt = uploadedAt;
	}
}
