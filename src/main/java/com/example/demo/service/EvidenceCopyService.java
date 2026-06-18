package com.example.demo.service;

import com.example.demo.domain.Evidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceCopyService {

    private final S3Client s3Client;

    @Value("${aws.s3.evidence-bucket}")
    private String evidenceBucket;

    public void prepareCopyForAnalysis(Evidence evidence) {
        if (evidence.getCopyStatus() == com.example.demo.domain.enums.CopyStatus.ACTIVE
                && evidence.getCopyStoragePath() != null) {
            return;
        }

        String caseKey = EvidenceStoragePaths.resolveCaseKey(evidence);
        String copyKey = EvidenceStoragePaths.copyKey(caseKey, evidence.getEvidenceId(), evidence.getFileName());

        s3Client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(evidenceBucket)
                .sourceKey(evidence.getOriginalStoragePath())
                .destinationBucket(evidenceBucket)
                .destinationKey(copyKey)
                .build());

        evidence.activateCopy(copyKey, evidence.getOriginalHashValue());
        log.info("Analysis copy prepared evidenceId={} copyKey={}", evidence.getEvidenceId(), copyKey);
    }
}
