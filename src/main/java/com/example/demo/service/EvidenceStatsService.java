package com.example.demo.service;

import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.dto.AnalysisTrendPoint;
import com.example.demo.dto.AnalysisTrendResponse;
import com.example.demo.dto.EvidenceStatsResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisRequestRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceStatsService {

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 30;

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

    /** RQ-DSH-044: 최근 N일 일별 완료 분석 건수 (꺾은선 차트용) */
    public AnalysisTrendResponse getAnalysisTrend(Long uploaderId, int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "days는 " + MIN_DAYS + "~" + MAX_DAYS + " 사이여야 합니다.");
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);
        List<AnalysisTrendPoint> points = new ArrayList<>(days);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            LocalDateTime startInclusive = date.atStartOfDay();
            LocalDateTime endExclusive = date.plusDays(1).atStartOfDay();
            long completedCount = analysisRequestRepository.countCompletedByUploaderCompletedAtBetween(
                    uploaderId, startInclusive, endExclusive);
            points.add(AnalysisTrendPoint.builder()
                    .date(date.toString())
                    .completedCount(completedCount)
                    .build());
        }

        return AnalysisTrendResponse.builder()
                .days(days)
                .points(points)
                .build();
    }
}
