package com.example.demo.service;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${analysis.worker.step-delay-ms:600}")
    private long stepDelayMs;

    public void processJob(Long analysisRequestId) {
        if (!prepareJob(analysisRequestId)) {
            return;
        }

        try {
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

            completeJob(analysisRequestId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Analysis job {} interrupted", analysisRequestId);
        } catch (Exception ex) {
            log.error("Analysis job {} failed", analysisRequestId, ex);
            failJob(analysisRequestId, ex.getMessage());
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

    private void completeJob(Long analysisRequestId) {
        transactionTemplate.executeWithoutResult(status -> {
            AnalysisRequest request = analysisRequestRepository.findById(analysisRequestId).orElse(null);
            if (request == null) {
                return;
            }

            request.setStatus(AnalysisStatus.COMPLETED);
            request.setProgressPercent(100);
            request.setCompletedAt(LocalDateTime.now());

            Long analysisResultId = analysisResultPersistenceService.saveSimulatedVideoResult(analysisRequestId);

            evidenceRepository.findById(request.getEvidenceId()).ifPresent(evidence -> {
                analysisCustodyLogService.recordAnalysisCompleted(request, evidence, analysisResultId);
                evidenceCopyService.deleteAnalysisCopy(evidence, request.getRequestedBy());
                notificationService.notifyAnalysisCompleted(
                        request.getRequestedBy(),
                        evidence.getEvidenceId(),
                        evidence.getFileName()
                );
            });
        });
    }

    private void failJob(Long analysisRequestId, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            AnalysisRequest request = analysisRequestRepository.findById(analysisRequestId).orElse(null);
            if (request == null) {
                return;
            }

            request.setStatus(AnalysisStatus.FAILED);
            request.setErrorCode("ANALYSIS_FAILED");
            request.setErrorMessage(message);
            request.setCompletedAt(LocalDateTime.now());

            evidenceRepository.findById(request.getEvidenceId()).ifPresent(evidence -> {
                analysisCustodyLogService.recordAnalysisFailed(request, evidence);
                evidenceCopyService.deleteAnalysisCopy(evidence, request.getRequestedBy());
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
