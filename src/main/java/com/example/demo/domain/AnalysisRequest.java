package com.example.demo.domain;

import com.example.demo.domain.enums.AnalysisStatus;
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
@Table(name = "analysis_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "analysis_request_id")
	private Long analysisRequestId;

	@Column(name = "evidence_id", nullable = false)
	private Long evidenceId;

	@Column(name = "requested_by", nullable = false)
	private Long requestedBy;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private AnalysisStatus status;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Builder
	public AnalysisRequest(
			Long evidenceId,
			Long requestedBy,
			AnalysisStatus status,
			LocalDateTime requestedAt,
			LocalDateTime startedAt,
			LocalDateTime completedAt
	) {
		this.evidenceId = evidenceId;
		this.requestedBy = requestedBy;
		this.status = status;
		this.requestedAt = requestedAt;
		this.startedAt = startedAt;
		this.completedAt = completedAt;
	}
}
