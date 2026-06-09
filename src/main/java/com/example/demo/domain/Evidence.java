package com.example.demo.domain;

import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	@Column(name = "uploader_id")
	private Long uploaderId;

	@Column(name = "case_number")
	private String caseNumber;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Enumerated(EnumType.STRING)
	@Column(name = "file_type")
	private FileType fileType;

	@Column(name = "mime_type")
	private String mimeType;

	@Column(name = "file_size")
	private Long fileSize;

	@Column(name = "hash_algorithm", nullable = false, length = 20)
	private String hashAlgorithm;

	@Column(name = "hash_value", nullable = false, length = 64)
	private String hashValue;

	@Column(name = "original_storage_path", nullable = false)
	private String originalStoragePath;

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private EvidenceStatus status;

	@Column(name = "uploaded_at", nullable = false)
	private LocalDateTime uploadedAt;

	@Builder
	public Evidence(
			Long uploaderId,
			String caseNumber,
			String fileName,
			FileType fileType,
			String mimeType,
			Long fileSize,
			String hashAlgorithm,
			String hashValue,
			String originalStoragePath,
			EvidenceStatus status,
			LocalDateTime uploadedAt
	) {
		this.uploaderId = uploaderId;
		this.caseNumber = caseNumber;
		this.fileName = fileName;
		this.fileType = fileType;
		this.mimeType = mimeType;
		this.fileSize = fileSize;
		this.hashAlgorithm = hashAlgorithm;
		this.hashValue = hashValue;
		this.originalStoragePath = originalStoragePath;
		this.status = status;
		this.uploadedAt = uploadedAt;
	}
}
