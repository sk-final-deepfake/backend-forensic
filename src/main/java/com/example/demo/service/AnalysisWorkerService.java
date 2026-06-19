package com.example.demo.service;

import com.example.demo.config.AnalysisWorkerProperties;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.dto.AnalysisResponseMessage;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisWorkerService {

    private static final int PROGRESS_STEPS = 10;

    private final AnalysisRequestRepository analysisRequestRepository;
    private final EvidenceRepository evidenceRepository;
    private final TransactionTemplate transactionTemplate;
    private final AnalysisResultPersistenceService analysisResultPersistenceService;
    private final AnalysisCustodyLogService analysisCustodyLogService;
    private final EvidenceCopyService evidenceCopyService;
    private final NotificationService notificationService;
    private final AnalysisWorkerProperties workerProperties;
    private final DashboardStatsCache dashboardStatsCache;

    public void processJob(Long analysisRequestId) {
        if (!prepareJob(analysisRequestId)) {
            return;
        }

        try {
            long stepDelayMs = workerProperties.getStepDelayMs();
            for (int step = 1; step <= PROGRESS_STEPS; step++) {
                if (!isJobActive(analysisRequestId)) {
                    log.info("Analysis job {} cancelled or removed", analysisRequestId);
                    return;
                }

                updateProgress(analysisRequestId, step * 10);
                if (stepDelayMs > 0) {
                    Thread.sleep(stepDelayMs);
                }
            }

            Long analysisResultId = analysisResultPersistenceService.saveSimulatedVideoResult(analysisRequestId);
            finalizeCompletedJob(analysisRequestId, analysisResultId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Analysis job {} interrupted", analysisRequestId);
        } catch (Exception ex) {
            log.error("Analysis job {} failed", analysisRequestId, ex);
            finalizeFailedJob(analysisRequestId, "ANALYSIS_FAILED", ex.getMessage());
        }
    }

    public void markDispatchedToAi(Long analysisRequestId) {
        prepareJob(analysisRequestId);
    }

    public void failStaleAnalysisJob(Long analysisRequestId) {
        AnalysisRequest request = analysisRequestRepository.findById(analysisRequestId).orElse(null);
        if (request == null) {
            return;
        }
        if (request.getStatus() != AnalysisStatus.QUEUED && request.getStatus() != AnalysisStatus.ANALYZING) {
            return;
        }
        finalizeFailedJob(
                analysisRequestId,
                "ANALYSIS_TIMEOUT",
                "분석 대기 또는 처리 시간이 허용 한도를 초과했습니다."
        );
    }

    public void applyAiResult(AnalysisResponseMessage response) {
        if (response == null || response.getAnalysisRequestId() == null) {
            log.warn("Ignored AI result message without analysisRequestId");
            return;
        }

        Long analysisRequestId = response.getAnalysisRequestId();
        if ("FAILED".equalsIgnoreCase(response.getStatus())) {
            String errorCode = response.getErrorCode() == null ? "AI_ANALYSIS_FAILED" : response.getErrorCode();
            String message = response.getMessage() == null ? "AI analysis failed." : response.getMessage();
            finalizeFailedJob(analysisRequestId, errorCode, message);
            return;
        }

        if (!"COMPLETED".equalsIgnoreCase(response.getStatus())) {
            log.warn("Ignored AI result message with unsupported status: {}", response.getStatus());
            return;
        }

        try {
            Long analysisResultId = analysisResultPersistenceService.saveFromAiResponse(response);
            finalizeCompletedJob(analysisRequestId, analysisResultId);
        } catch (Exception ex) {
            log.error("Failed to persist AI result for analysisRequestId={}", analysisRequestId, ex);
            finalizeFailedJob(analysisRequestId, "AI_RESULT_PERSIST_FAILED", ex.getMessage());
        }
    }

    private boolean prepareJob(Long analysisRequestId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            AnalysisRequest request = analysisRequestRepository.findById(analysisRequestId).orElse(null);
            if (request == null || request.getStatus() != AnalysisStatus.QUEUED) {
                return false;
            }

            request.setStatus(AnalysisStatus.ANALYZING);
            request.setStartedAt(LocalDateTime.now());
            request.setProgressPercent(0);

            Evidence evidence = evidenceRepository.findById(request.getEvidenceId()).orElse(null);
            if (evidence != null) {
                analysisCustodyLogService.recordAnalysisStarted(request, evidence);
            }
            return true;
        }));
    }

    private void updateProgress(Long analysisRequestId, int progressPercent) {
        transactionTemplate.executeWithoutResult(status ->
                analysisRequestRepository.findById(analysisRequestId).ifPresent(request -> {
                    if (request.getStatus() == AnalysisStatus.ANALYZING) {
                        request.setProgressPercent(progressPercent);
                    }
                })
        );
    }

    private void finalizeCompletedJob(Long analysisRequestId, Long analysisResultId) {
        transactionTemplate.executeWithoutResult(status -> {
            AnalysisRequest request = analysisRequestRepository.findById(analysisRequestId).orElse(null);
            if (request == null) {
                return;
            }

            request.setStatus(AnalysisStatus.COMPLETED);
            request.setProgressPercent(100);
            request.setCompletedAt(LocalDateTime.now());

            evidenceRepository.findById(request.getEvidenceId()).ifPresent(evidence -> {
                analysisCustodyLogService.recordAnalysisCompleted(request, evidence, analysisResultId);
                evidenceCopyService.deleteAnalysisCopy(evidence, request.getRequestedBy());
                dashboardStatsCache.invalidate(request.getRequestedBy());
                notificationService.notifyAnalysisCompleted(
                        request.getRequestedBy(),
                        evidence.getEvidenceId(),
                        evidence.getFileName()
                );
            });
        });
    }

    private void finalizeFailedJob(Long analysisRequestId, String errorCode, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            AnalysisRequest request = analysisRequestRepository.findById(analysisRequestId).orElse(null);
            if (request == null) {
                return;
            }

            request.setStatus(AnalysisStatus.FAILED);
            request.setErrorCode(errorCode);
            request.setErrorMessage(message);
            request.setCompletedAt(LocalDateTime.now());

            evidenceRepository.findById(request.getEvidenceId()).ifPresent(evidence -> {
                analysisCustodyLogService.recordAnalysisFailed(request, evidence);
                evidenceCopyService.deleteAnalysisCopy(evidence, request.getRequestedBy());
                dashboardStatsCache.invalidate(request.getRequestedBy());
                notificationService.notifyAnalysisFailed(
                        request.getRequestedBy(),
                        evidence.getEvidenceId(),
                        evidence.getFileName()
                );
            });
        });
    }

    private boolean isJobActive(Long analysisRequestId) {
        return analysisRequestRepository.findById(analysisRequestId)
                .map(request -> request.getStatus() == AnalysisStatus.ANALYZING)
                .orElse(false);
    }
}
