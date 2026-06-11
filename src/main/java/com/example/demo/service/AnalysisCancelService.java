package com.example.demo.service;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Slf4j
@Service
public class AnalysisCancelService {

    private final EvidenceRepository evidenceRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final CustodyLogService custodyLogService;
    private final S3Client s3Client;
    private final String evidenceBucket;

    public AnalysisCancelService(
            EvidenceRepository evidenceRepository,
            AnalysisRequestRepository analysisRequestRepository,
            CustodyLogService custodyLogService,
            S3Client s3Client,
            @Value("${aws.s3.evidence-bucket}") String evidenceBucket
    ) {
        this.evidenceRepository = evidenceRepository;
        this.analysisRequestRepository = analysisRequestRepository;
        this.custodyLogService = custodyLogService;
        this.s3Client = s3Client;
        this.evidenceBucket = evidenceBucket;
    }

    @Transactional
    public void cancelAnalysis(User user, Long evidenceId) {
        Evidence evidence = evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("증거를 찾을 수 없습니다."));

        AnalysisRequest request = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElseThrow(() -> new IllegalStateException("분석 요청을 찾을 수 없습니다."));

        if (request.getStatus() == AnalysisStatus.COMPLETED) {
            throw new IllegalStateException("완료된 분석은 중단할 수 없습니다.");
        }

        analysisRequestRepository.delete(request);
        deleteS3Object(evidence.getOriginalStoragePath());
        evidence.softDelete();
        custodyLogService.recordEvidenceAction(user, evidence, "EVIDENCE_DELETED", evidence.getFileName());
    }

    private void deleteS3Object(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            return;
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(evidenceBucket)
                    .key(s3Key)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to delete S3 object {}: {}", s3Key, e.getMessage());
        }
    }
}
