package com.example.demo.service;

import com.example.demo.domain.enums.FileType;
import com.example.demo.dto.EvidenceStatsResponse;
import com.example.demo.repository.AnalysisRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceStatsService {

    private final AnalysisRequestRepository analysisRequestRepository;

    /**
     * 로그인 사용자 기준 미디어별 분석 건수.
     * 분석 시작(AnalysisRequest 등록)된 증거만 집계합니다.
     */
    public EvidenceStatsResponse getMediaStats(Long uploaderId) {
        return EvidenceStatsResponse.builder()
                .imageCount(analysisRequestRepository.countByFileTypeAndUploaderWithAnalysisRequest(
                        FileType.IMAGE, uploaderId))
                .videoCount(analysisRequestRepository.countByFileTypeAndUploaderWithAnalysisRequest(
                        FileType.VIDEO, uploaderId))
                .audioCount(analysisRequestRepository.countByFileTypeAndUploaderWithAnalysisRequest(
                        FileType.AUDIO, uploaderId))
                .build();
    }
}
