package com.example.demo.service;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.repository.AnalysisRequestRepository;
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
    private static final long STEP_DELAY_MS = 600;

    private final AnalysisRequestRepository analysisRequestRepository;
    private final TransactionTemplate transactionTemplate;
    private final AnalysisResultPersistenceService analysisResultPersistenceService;

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
                Thread.sleep(STEP_DELAY_MS);
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
            analysisRequestRepository.findById(analysisRequestId).ifPresent(request -> {
                request.setStatus(AnalysisStatus.COMPLETED);
                request.setProgressPercent(100);
                request.setCompletedAt(LocalDateTime.now());
            });
            analysisResultPersistenceService.saveSimulatedVideoResult(analysisRequestId);
        });
    }

    private void failJob(Long analysisRequestId, String message) {
        transactionTemplate.executeWithoutResult(status ->
                analysisRequestRepository.findById(analysisRequestId).ifPresent(request -> {
                    request.setStatus(AnalysisStatus.FAILED);
                    request.setErrorCode("ANALYSIS_FAILED");
                    request.setErrorMessage(message);
                    request.setCompletedAt(LocalDateTime.now());
                })
        );
    }

    private boolean isJobActive(Long analysisRequestId) {
        return analysisRequestRepository.findById(analysisRequestId)
                .map(request -> request.getStatus() == AnalysisStatus.ANALYZING)
                .orElse(false);
    }
}
