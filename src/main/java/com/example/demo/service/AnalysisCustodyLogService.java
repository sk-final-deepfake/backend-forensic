package com.example.demo.service;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.CustodyTargetType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalysisCustodyLogService {

    static final String STEP_COPY_CREATED = "ANALYSIS_COPY_CREATED";
    static final String STEP_COPY_VERIFIED = "ANALYSIS_COPY_VERIFIED";
    static final String STEP_COPY_DELETED = "ANALYSIS_COPY_DELETED";

    static final String ERR_COPY_CREATE = "ANALYSIS_COPY_CREATE_FAILED";
    static final String ERR_COPY_VERIFY = "ANALYSIS_COPY_VERIFY_FAILED";
    static final String ERR_COPY_DELETE = "ANALYSIS_COPY_DELETE_FAILED";

    private final CustodyLogService custodyLogService;
    private final ObjectMapper objectMapper;

    public void recordCopyCreated(Long actorId, Evidence evidence) {
        custodyLogService.record(
                actorId,
                CustodyTargetType.EVIDENCE,
                evidence.getEvidenceId(),
                "ANALYSIS_COPY_CREATED",
                evidence.getCopyHashValue(),
                evidence.getCopyStoragePath(),
                "분석용 증거 사본 생성",
                toJson(copyPayload(evidence, "ACTIVE")),
                null
        );
    }

    public void recordCopyVerified(Long actorId, Evidence evidence) {
        Map<String, Object> payload = copyPayload(evidence, evidence.getCopyStatus().name());
        payload.put("originalHashValue", evidence.getOriginalHashValue());
        payload.put("copyHashValue", evidence.getCopyHashValue());
        payload.put("verified", true);

        custodyLogService.record(
                actorId,
                CustodyTargetType.EVIDENCE,
                evidence.getEvidenceId(),
                "ANALYSIS_COPY_VERIFIED",
                evidence.getCopyHashValue(),
                evidence.getCopyStoragePath(),
                "원본·사본 SHA-256 일치 검증 완료",
                toJson(payload),
                null
        );
    }

    public void recordCopyDeleted(Long actorId, Evidence evidence, String deletedCopyPath) {
        Map<String, Object> payload = copyPayload(evidence, "DELETED");
        payload.put("deletedCopyPath", deletedCopyPath);

        custodyLogService.record(
                actorId,
                CustodyTargetType.EVIDENCE,
                evidence.getEvidenceId(),
                "ANALYSIS_COPY_DELETED",
                evidence.getCopyHashValue(),
                deletedCopyPath,
                "분석용 증거 사본 삭제",
                toJson(payload),
                null
        );
    }

    public void recordCopyError(
            Long actorId,
            Evidence evidence,
            String step,
            String errorCode,
            String message
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", step);
        payload.put("errorCode", errorCode);
        payload.put("message", message);
        payload.put("evidenceId", evidence.getEvidenceId());
        if (evidence.getCopyStoragePath() != null) {
            payload.put("copyStoragePath", evidence.getCopyStoragePath());
        }

        custodyLogService.record(
                actorId,
                CustodyTargetType.EVIDENCE,
                evidence.getEvidenceId(),
                "ERROR_OCCURRED",
                evidence.getOriginalHashValue(),
                evidence.getCopyStoragePath() != null
                        ? evidence.getCopyStoragePath()
                        : evidence.getOriginalStoragePath(),
                message,
                toJson(payload),
                null
        );
    }

    public void recordAnalysisStarted(AnalysisRequest request, Evidence evidence) {
        custodyLogService.record(
                request.getRequestedBy(),
                CustodyTargetType.ANALYSIS_REQUEST,
                request.getAnalysisRequestId(),
                "ANALYSIS_STARTED",
                subjectHash(evidence),
                storagePathForAnalysis(evidence),
                "AI 분석 시작",
                toJson(analysisPayload(request, evidence, "ANALYZING")),
                null
        );
    }

    public void recordAnalysisCompleted(AnalysisRequest request, Evidence evidence, Long analysisResultId) {
        Map<String, Object> payload = analysisPayload(request, evidence, "COMPLETED");
        payload.put("analysisResultId", analysisResultId);

        custodyLogService.record(
                request.getRequestedBy(),
                CustodyTargetType.ANALYSIS_RESULT,
                analysisResultId,
                "ANALYSIS_COMPLETED",
                subjectHash(evidence),
                storagePathForAnalysis(evidence),
                "AI 분석 완료",
                toJson(payload),
                null
        );
    }

    public void recordAnalysisFailed(AnalysisRequest request, Evidence evidence) {
        Map<String, Object> payload = analysisPayload(request, evidence, "FAILED");
        payload.put("errorCode", request.getErrorCode());
        payload.put("errorMessage", request.getErrorMessage());

        custodyLogService.record(
                request.getRequestedBy(),
                CustodyTargetType.ANALYSIS_REQUEST,
                request.getAnalysisRequestId(),
                "ANALYSIS_FAILED",
                subjectHash(evidence),
                storagePathForAnalysis(evidence),
                "AI 분석 실패",
                toJson(payload),
                null
        );
    }

    private Map<String, Object> copyPayload(Evidence evidence, String copyStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceId", evidence.getEvidenceId());
        payload.put("copyStatus", copyStatus);
        payload.put("copyStoragePath", evidence.getCopyStoragePath());
        payload.put("hashAlgorithm", evidence.getHashAlgorithm());
        return payload;
    }

    private Map<String, Object> analysisPayload(AnalysisRequest request, Evidence evidence, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("analysisRequestId", request.getAnalysisRequestId());
        payload.put("evidenceId", evidence.getEvidenceId());
        payload.put("status", status);
        payload.put("fileType", "video");
        return payload;
    }

    private String subjectHash(Evidence evidence) {
        if (evidence.getCopyHashValue() != null && !evidence.getCopyHashValue().isBlank()) {
            return evidence.getCopyHashValue();
        }
        return evidence.getOriginalHashValue();
    }

    private String storagePathForAnalysis(Evidence evidence) {
        if (evidence.getCopyStoragePath() != null && !evidence.getCopyStoragePath().isBlank()) {
            return evidence.getCopyStoragePath();
        }
        return evidence.getOriginalStoragePath();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize custody log payload", ex);
        }
    }
}
