package com.example.demo.service;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.dto.AnalysisStatusResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisStatusService {

    private final EvidenceRepository evidenceRepository;
    private final AnalysisRequestRepository analysisRequestRepository;

    public AnalysisStatusResponse getStatus(User user, Long evidenceId) {
        Evidence evidence = evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, user.getUserId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거를 찾을 수 없습니다."));

        return analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidence.getEvidenceId())
                .map(request -> {
                    AnalysisStatusResponse.AnalysisStatusResponseBuilder builder = AnalysisStatusResponse.builder()
                            .evidenceId(evidence.getEvidenceId())
                            .analysisRequestId(request.getAnalysisRequestId())
                            .status(toApiStatus(request.getStatus()))
                            .progressPercent(request.getProgressPercent());
                    if (request.getStatus() == AnalysisStatus.FAILED) {
                        builder.errorCode(request.getErrorCode())
                                .errorMessage(request.getErrorMessage());
                    }
                    return builder.build();
                })
                .orElseGet(() -> AnalysisStatusResponse.builder()
                        .evidenceId(evidence.getEvidenceId())
                        .analysisRequestId(0L)
                        .status("PENDING")
                        .progressPercent(0)
                        .build());
    }

    private String toApiStatus(AnalysisStatus status) {
        return switch (status) {
            case QUEUED -> "PENDING";
            case ANALYZING -> "PROCESSING";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
        };
    }
}
