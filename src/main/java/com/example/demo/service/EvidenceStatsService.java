package com.example.demo.service;

import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.dto.EvidenceStatsResponse;
import com.example.demo.repository.AnalysisRequestRepository;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceStatsService {

    private final AnalysisRequestRepository analysisRequestRepository;

    /** RQ-DSH-043: 대시보드 통계 카드 4종 */
    public EvidenceStatsResponse getDashboardStats(Long uploaderId) {
        return EvidenceStatsResponse.builder()
                .totalAnalysisCount(analysisRequestRepository.countTotalByUploader(uploaderId))
                .deepfakeDetectedCount(analysisRequestRepository.countDeepfakeDetectedByUploader(uploaderId))
                .completedCount(analysisRequestRepository.countByUploaderAndStatus(
                        uploaderId, AnalysisStatus.COMPLETED))
                .inProgressCount(analysisRequestRepository.countByUploaderAndStatusIn(
                        uploaderId, List.of(AnalysisStatus.QUEUED, AnalysisStatus.ANALYZING)))
                .build();
    }
}
