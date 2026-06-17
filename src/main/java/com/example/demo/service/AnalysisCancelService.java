package com.example.demo.service;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AnalysisCancelService {

    private final EvidenceRepository evidenceRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final CustodyLogService custodyLogService;

    public AnalysisCancelService(
            EvidenceRepository evidenceRepository,
            AnalysisRequestRepository analysisRequestRepository,
            CustodyLogService custodyLogService
    ) {
        this.evidenceRepository = evidenceRepository;
        this.analysisRequestRepository = analysisRequestRepository;
        this.custodyLogService = custodyLogService;
    }

    @Transactional
    public void cancelAnalysis(User user, Long evidenceId) {
        Evidence evidence = evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, user.getUserId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거를 찾을 수 없습니다."));

        AnalysisRequest request = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.BAD_REQUEST, "ANALYSIS_NOT_CANCELABLE", "분석 요청을 찾을 수 없습니다."));

        if (request.getStatus() != AnalysisStatus.QUEUED
                && request.getStatus() != AnalysisStatus.ANALYZING) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "ANALYSIS_NOT_CANCELABLE",
                    "대기 또는 진행 중인 분석만 중단할 수 있습니다.");
        }

        analysisRequestRepository.delete(request);
        custodyLogService.recordEvidenceAction(
                user,
                evidence,
                "ANALYSIS_CANCELLED",
                evidence.getFileName()
        );
    }
}
