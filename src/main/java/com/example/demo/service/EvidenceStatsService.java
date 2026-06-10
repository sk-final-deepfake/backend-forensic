package com.example.demo.service;

import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.dto.EvidenceStatsResponse;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceStatsService {

    private final EvidenceRepository evidenceRepository;
    private final AnalysisRequestRepository analysisRequestRepository;

    /**
     * 메인 대시보드 미디어별 건수.
     * 분석 완료 건이 있으면 ERD 정책(완료 분석 기준)으로 집계하고,
     * 아직 분석 파이프라인이 없을 때는 업로드된 증거 건수로 대체합니다.
     */
    public EvidenceStatsResponse getMediaStats() {
        long imageCompleted = countCompleted(FileType.IMAGE);
        long videoCompleted = countCompleted(FileType.VIDEO);
        long audioCompleted = countCompleted(FileType.AUDIO);

        if (imageCompleted + videoCompleted + audioCompleted > 0) {
            return EvidenceStatsResponse.builder()
                    .imageCount(imageCompleted)
                    .videoCount(videoCompleted)
                    .audioCount(audioCompleted)
                    .build();
        }

        return EvidenceStatsResponse.builder()
                .imageCount(evidenceRepository.countByFileTypeAndDeletedAtIsNull(FileType.IMAGE))
                .videoCount(evidenceRepository.countByFileTypeAndDeletedAtIsNull(FileType.VIDEO))
                .audioCount(evidenceRepository.countByFileTypeAndDeletedAtIsNull(FileType.AUDIO))
                .build();
    }

    private long countCompleted(FileType fileType) {
        return analysisRequestRepository.countCompletedAnalysesByFileType(
                fileType,
                AnalysisStatus.COMPLETED
        );
    }
}
