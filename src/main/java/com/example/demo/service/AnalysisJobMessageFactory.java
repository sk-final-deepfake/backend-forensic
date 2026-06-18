package com.example.demo.service;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.dto.AnalysisJobMessage;
import com.example.demo.util.ApiDateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalysisJobMessageFactory {

    private final S3AnalysisAccessService s3AnalysisAccessService;

    public AnalysisJobMessage buildForGpuDispatch(
            Evidence evidence,
            AnalysisRequest analysisRequest,
            String caseName
    ) {
        String copyKey = evidence.getCopyStoragePath();
        String presignedDownloadUrl = s3AnalysisAccessService.createGpuDownloadUrl(copyKey);

        return AnalysisJobMessage.builder()
                .analysisRequestId(analysisRequest.getAnalysisRequestId())
                .evidenceId(evidence.getEvidenceId())
                .fileType("video")
                .filePath(copyKey)
                .s3ObjectKey(copyKey)
                .s3Bucket(s3AnalysisAccessService.getEvidenceBucket())
                .s3Region(s3AnalysisAccessService.getAwsRegion())
                .presignedDownloadUrl(presignedDownloadUrl)
                .originalHash(evidence.getOriginalHashValue())
                .originalSha256(evidence.getOriginalHashValue())
                .caseName(caseName)
                .requestedAt(ApiDateTimeFormatter.formatUtc(analysisRequest.getRequestedAt()))
                .build();
    }
}
