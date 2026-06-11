package com.example.demo.service;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.dto.StartAnalysisResponse;
import com.example.demo.messaging.AnalysisQueueMessage;
import com.example.demo.messaging.AnalysisQueuePublisher;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final String RABBITMQ_PUBLISH_FAILED = "RABBITMQ_PUBLISH_FAILED";
    private static final String RABBITMQ_PUBLISH_STEP = "RABBITMQ_PUBLISH";
    private static final String RABBITMQ_PUBLISH_FAILURE_MESSAGE = "분석 요청 큐 등록에 실패했습니다.";

    private final EvidenceRepository evidenceRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final CustodyLogService custodyLogService;
    private final ObjectMapper objectMapper;
    private final AnalysisQueuePublisher analysisQueuePublisher;

    @Transactional
    public StartAnalysisResponse startAnalysis(User user, List<Long> evidenceIds, String caseName) {
        if (caseName == null || caseName.isBlank()) {
            throw new IllegalArgumentException("사건명은 필수입니다.");
        }
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("분석할 증거를 선택해 주세요.");
        }

        String trimmedCaseName = caseName.trim();
        List<Evidence> evidences = evidenceRepository
                .findByEvidenceIdInAndUploaderIdAndDeletedAtIsNull(evidenceIds, user.getUserId());

        if (evidences.isEmpty()) {
            throw new IllegalArgumentException("분석할 수 있는 증거를 찾을 수 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> startedEvidenceIds = new ArrayList<>();

        for (Evidence evidence : evidences) {
            evidence.updateCaseInfo(trimmedCaseName);

            if (analysisRequestRepository.existsByEvidenceId(evidence.getEvidenceId())) {
                continue;
            }

            AnalysisRequest request = new AnalysisRequest();
            request.setEvidenceId(evidence.getEvidenceId());
            request.setRequestedBy(user.getUserId());
            request.setStatus(AnalysisStatus.QUEUED);
            request.setRequestedAt(now);
            AnalysisRequest savedRequest = analysisRequestRepository.save(request);
            try {
                analysisQueuePublisher.publish(toQueueMessage(user, evidence, savedRequest, trimmedCaseName));
                recordAnalysisRequestedLog(user, evidence, savedRequest, trimmedCaseName);
                startedEvidenceIds.add(evidence.getEvidenceId());
            } catch (Exception ex) {
                savedRequest.setStatus(AnalysisStatus.FAILED);
                savedRequest.setErrorCode(RABBITMQ_PUBLISH_FAILED);
                savedRequest.setErrorMessage(RABBITMQ_PUBLISH_FAILURE_MESSAGE);
                analysisRequestRepository.save(savedRequest);
                recordQueuePublishErrorLog(user, evidence, savedRequest);
            }
        }

        return StartAnalysisResponse.builder()
                .success(true)
                .message("분석 요청이 등록되었습니다.")
                .caseName(trimmedCaseName)
                .startedCount(startedEvidenceIds.size())
                .evidenceIds(startedEvidenceIds)
                .build();
    }

    private AnalysisQueueMessage toQueueMessage(
            User user,
            Evidence evidence,
            AnalysisRequest savedRequest,
            String caseName
    ) {
        return new AnalysisQueueMessage(
                savedRequest.getAnalysisRequestId(),
                evidence.getEvidenceId(),
                user.getUserId(),
                caseName,
                evidence.getOriginalHashValue(),
                evidence.getOriginalStoragePath()
        );
    }

    private void recordAnalysisRequestedLog(
            User user,
            Evidence evidence,
            AnalysisRequest savedRequest,
            String caseName
    ) {
        custodyLogService.record(
                user.getUserId(),
                CustodyTargetType.ANALYSIS_REQUEST,
                savedRequest.getAnalysisRequestId(),
                "ANALYSIS_REQUESTED",
                evidence.getOriginalHashValue(),
                evidence.getOriginalStoragePath(),
                "AI 분석 요청 생성 및 큐 등록 완료",
                toJson(analysisRequestedPayload(evidence, savedRequest, caseName)),
                null
        );
    }

    private void recordQueuePublishErrorLog(
            User user,
            Evidence evidence,
            AnalysisRequest savedRequest
    ) {
        custodyLogService.record(
                user.getUserId(),
                CustodyTargetType.ANALYSIS_REQUEST,
                savedRequest.getAnalysisRequestId(),
                "ERROR_OCCURRED",
                evidence.getOriginalHashValue(),
                evidence.getOriginalStoragePath(),
                "분석 요청 큐 등록 실패",
                toJson(queuePublishErrorPayload(evidence, savedRequest)),
                null
        );
    }

    private Map<String, Object> analysisRequestedPayload(
            Evidence evidence,
            AnalysisRequest savedRequest,
            String caseName
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceId", evidence.getEvidenceId());
        payload.put("analysisRequestId", savedRequest.getAnalysisRequestId());
        payload.put("status", savedRequest.getStatus().name());
        payload.put("caseName", caseName);
        payload.put("queueRegistered", true);
        payload.put("queueName", queueName());
        return payload;
    }

    private Map<String, Object> queuePublishErrorPayload(
            Evidence evidence,
            AnalysisRequest savedRequest
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", RABBITMQ_PUBLISH_STEP);
        payload.put("errorCode", RABBITMQ_PUBLISH_FAILED);
        payload.put("message", RABBITMQ_PUBLISH_FAILURE_MESSAGE);
        payload.put("evidenceId", evidence.getEvidenceId());
        payload.put("analysisRequestId", savedRequest.getAnalysisRequestId());
        payload.put("queueName", queueName());
        return payload;
    }

    private String queueName() {
        String queueName = analysisQueuePublisher.queueName();
        return queueName == null || queueName.isBlank() ? "forenshield.analysis.requests" : queueName;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize custody log payload", e);
        }
    }
}
