package com.example.demo.service;

import com.example.demo.config.AnalysisMessagingProperties;
import com.example.demo.config.AnalysisWorkerProperties;
import com.example.demo.config.RabbitMqConfig;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.dto.AnalysisJobMessage;
import com.example.demo.dto.StartAnalysisRequest;
import com.example.demo.dto.StartAnalysisResponse;
import com.example.demo.exception.AnalysisCopyException;
import com.example.demo.exception.AnalysisDispatchException;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private static final String S3_COPY_NOT_READY = "S3_COPY_NOT_READY";
    private static final String RABBITMQ_PUBLISH_STEP = "RABBITMQ_PUBLISH";
    private static final String RABBITMQ_PUBLISH_FAILURE_MESSAGE = "분석 요청 큐 등록에 실패했습니다.";

    private final EvidenceRepository evidenceRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisJobEnqueuer analysisJobEnqueuer;
    private final AnalysisJobMessageFactory analysisJobMessageFactory;
    private final EvidenceCopyService evidenceCopyService;
    private final CustodyLogService custodyLogService;
    private final ObjectMapper objectMapper;
    private final AnalysisWorkerProperties workerProperties;
    private final AnalysisWorkerService analysisWorkerService;
    private final AnalysisMessagingProperties messagingProperties;

    @Transactional
    public StartAnalysisResponse startAnalysis(User user, StartAnalysisRequest request) {
        List<Long> evidenceIds = resolveEvidenceIds(request);
        String trimmedCaseName = resolveCaseName(request.getCaseName(), evidenceIds, user.getUserId());

        List<Evidence> evidences = evidenceRepository
                .findByEvidenceIdInAndUploaderIdAndDeletedAtIsNull(evidenceIds, user.getUserId());

        if (evidences.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "분석할 수 있는 증거를 찾을 수 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> startedEvidenceIds = new ArrayList<>();

        for (Evidence evidence : evidences) {
            if (analysisRequestRepository.existsByEvidenceIdAndStatus(
                    evidence.getEvidenceId(), AnalysisStatus.COMPLETED)) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "ANALYSIS_ALREADY_COMPLETED",
                        "이미 분석이 완료된 증거입니다.");
            }

            if (analysisRequestRepository.existsByEvidenceIdAndStatusIn(
                    evidence.getEvidenceId(), List.of(AnalysisStatus.QUEUED, AnalysisStatus.ANALYZING))) {
                continue;
            }

            evidence.updateCaseInfo(trimmedCaseName);
            try {
                evidenceCopyService.prepareCopyForAnalysis(evidence, user.getUserId());
            } catch (AnalysisCopyException ex) {
                throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ex.getErrorCode(),
                        ex.getMessage()
                );
            }
            evidenceRepository.save(evidence);

            AnalysisRequest analysisRequest = new AnalysisRequest();
            analysisRequest.setEvidenceId(evidence.getEvidenceId());
            analysisRequest.setRequestedBy(user.getUserId());
            analysisRequest.setStatus(AnalysisStatus.QUEUED);
            analysisRequest.setProgressPercent(0);
            analysisRequest.setRequestedAt(now);
            AnalysisRequest savedRequest = analysisRequestRepository.save(analysisRequest);

            try {
                AnalysisJobMessage message = analysisJobMessageFactory.buildForGpuDispatch(
                        evidence, savedRequest, trimmedCaseName);
                analysisJobEnqueuer.enqueue(message);
                recordAnalysisRequestedLog(user, evidence, savedRequest, trimmedCaseName, message);
                if (workerProperties.isAiMode()) {
                    analysisWorkerService.markDispatchedToAi(savedRequest.getAnalysisRequestId());
                }
                startedEvidenceIds.add(evidence.getEvidenceId());
            } catch (AnalysisDispatchException ex) {
                savedRequest.setStatus(AnalysisStatus.FAILED);
                savedRequest.setErrorCode(ex.getErrorCode());
                savedRequest.setErrorMessage(ex.getMessage());
                analysisRequestRepository.save(savedRequest);
                recordDispatchErrorLog(user, evidence, savedRequest, ex.getErrorCode(), ex.getMessage());
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

    private List<Long> resolveEvidenceIds(StartAnalysisRequest request) {
        if (request.getEvidenceId() != null) {
            return List.of(request.getEvidenceId());
        }
        if (request.getEvidenceIds() != null && !request.getEvidenceIds().isEmpty()) {
            return request.getEvidenceIds();
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "분석할 증거를 선택해 주세요.");
    }

    private String resolveCaseName(String caseName, List<Long> evidenceIds, Long uploaderId) {
        if (caseName != null && !caseName.isBlank()) {
            return caseName.trim();
        }

        List<Evidence> evidences = evidenceRepository
                .findByEvidenceIdInAndUploaderIdAndDeletedAtIsNull(evidenceIds, uploaderId);
        return evidences.stream()
                .map(Evidence::getCaseName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "사건명은 필수입니다."));
    }

    private void recordAnalysisRequestedLog(
            User user,
            Evidence evidence,
            AnalysisRequest savedRequest,
            String caseName,
            AnalysisJobMessage message
    ) {
        custodyLogService.record(
                user.getUserId(),
                CustodyTargetType.ANALYSIS_REQUEST,
                savedRequest.getAnalysisRequestId(),
                "ANALYSIS_REQUESTED",
                evidence.getOriginalHashValue(),
                evidence.getCopyStoragePath() != null ? evidence.getCopyStoragePath() : evidence.getOriginalStoragePath(),
                "AI 분석 요청 생성 및 큐 등록 완료",
                toJson(analysisRequestedPayload(evidence, savedRequest, caseName, message)),
                null
        );
    }

    private void recordDispatchErrorLog(
            User user,
            Evidence evidence,
            AnalysisRequest savedRequest,
            String errorCode,
            String message
    ) {
        custodyLogService.record(
                user.getUserId(),
                CustodyTargetType.ANALYSIS_REQUEST,
                savedRequest.getAnalysisRequestId(),
                "ERROR_OCCURRED",
                evidence.getOriginalHashValue(),
                evidence.getOriginalStoragePath(),
                S3_COPY_NOT_READY.equals(errorCode)
                        ? "분석용 S3 사본 확인 실패"
                        : "분석 요청 큐 등록 실패",
                toJson(dispatchErrorPayload(evidence, savedRequest, errorCode, message)),
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
            String caseName,
            AnalysisJobMessage message
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceId", evidence.getEvidenceId());
        payload.put("analysisRequestId", savedRequest.getAnalysisRequestId());
        payload.put("status", savedRequest.getStatus().name());
        payload.put("caseName", caseName);
        payload.put("fileType", "video");
        payload.put("filePath", message.getFilePath());
        payload.put("s3Bucket", message.getS3Bucket());
        payload.put("s3Region", message.getS3Region());
        payload.put("presignedDownloadUrl", message.getPresignedDownloadUrl());
        payload.put("queueRegistered", true);
        payload.put("queueName", queueName());
        payload.put("exchange", messagingProperties.getAnalysisExchange());
        payload.put("routingKey", messagingProperties.getVideoAnalysisRoutingKey());
        return payload;
    }

    private Map<String, Object> dispatchErrorPayload(
            Evidence evidence,
            AnalysisRequest savedRequest,
            String errorCode,
            String message
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("step", RABBITMQ_PUBLISH_STEP);
        payload.put("errorCode", errorCode);
        payload.put("message", message);
        payload.put("evidenceId", evidence.getEvidenceId());
        payload.put("analysisRequestId", savedRequest.getAnalysisRequestId());
        payload.put("queueName", queueName());
        payload.put("exchange", messagingProperties.getAnalysisExchange());
        payload.put("routingKey", messagingProperties.getVideoAnalysisRoutingKey());
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
        return RabbitMqConfig.ANALYSIS_QUEUE;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize custody log payload", e);
        }
    }
}
