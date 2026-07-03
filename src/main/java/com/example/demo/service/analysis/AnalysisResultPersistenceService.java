package com.example.demo.service.analysis;

import com.example.demo.config.VideoFrameAnalysisProperties;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.dto.AnalysisResponseMessage;
import com.example.demo.dto.FrameRiskDto;
import com.example.demo.dto.SuspiciousSegmentDto;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.util.ApiDateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisResultPersistenceService {

    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final SuspiciousSegmentCalculator suspiciousSegmentCalculator;
    private final VideoFrameAnalysisProperties frameAnalysisProperties;
    private final AnalysisResponseResolver responseResolver;
    private final VideoAnalysisModuleWriter moduleWriter;

    @Transactional
    public Long saveSimulatedVideoResult(Long analysisRequestId) {
        return analysisResultRepository.findByAnalysisRequestId(analysisRequestId)
                .map(AnalysisResult::getAnalysisResultId)
                .orElseGet(() -> createSimulatedVideoResult(analysisRequestId));
    }

    @Transactional
    public Long saveFromAiResponse(AnalysisResponseMessage response) {
        Long analysisRequestId = response.getAnalysisRequestId();
        return analysisResultRepository.findByAnalysisRequestId(analysisRequestId)
                .map(AnalysisResult::getAnalysisResultId)
                .orElseGet(() -> createFromAiResponse(response));
    }

    private Long createFromAiResponse(AnalysisResponseMessage response) {
        AnalysisRequest request = analysisRequestRepository.findById(response.getAnalysisRequestId())
                .orElseThrow(() -> new IllegalStateException(
                        "AnalysisRequest not found: " + response.getAnalysisRequestId()));

        AnalysisResult savedResult = analysisResultRepository.save(buildResult(
                request.getAnalysisRequestId(),
                response.getRiskScore(),
                response.getConfidenceScore(),
                parseRiskLevel(response.getRiskLevel()),
                buildSummary(response),
                ApiDateTimeFormatter.parseUtc(response.getAnalyzedAt())
        ));

        persistVideoModules(savedResult.getAnalysisResultId(), response);
        return savedResult.getAnalysisResultId();
    }

    private Long createSimulatedVideoResult(Long analysisRequestId) {
        analysisRequestRepository.findById(analysisRequestId)
                .orElseThrow(() -> new IllegalStateException("AnalysisRequest not found: " + analysisRequestId));

        AnalysisResult savedResult = analysisResultRepository.save(buildResult(
                analysisRequestId,
                72.5,
                0.91,
                RiskLevel.HIGH,
                "Local worker: simulated video analysis completed (see ai-json.md).",
                LocalDateTime.now()
        ));

        List<FrameRiskDto> frameRisks = buildSimulatedFrameRisks();
        List<SuspiciousSegmentDto> segments = suspiciousSegmentCalculator.compute(
                frameRisks,
                frameAnalysisProperties.getHighRiskFrameScoreThreshold(),
                frameAnalysisProperties.getMinSuspiciousSegmentSec()
        );

        moduleWriter.writeVideoAnalysisModules(
                savedResult.getAnalysisResultId(),
                buildSimulatedVideoResultItem(frameRisks, segments),
                null,
                0.91,
                frameRisks,
                segments
        );
        return savedResult.getAnalysisResultId();
    }

    private void persistVideoModules(Long analysisResultId, AnalysisResponseMessage response) {
        AnalysisResponseMessage.AnalysisVideoResultItem videoResult = responseResolver.findVideoResult(
                response.getResults()
        );
        if (videoResult == null) {
            return;
        }

        List<FrameRiskDto> frameRisks = responseResolver.toFrameRiskDtos(videoResult.getFrameRisks());
        List<SuspiciousSegmentDto> suspiciousSegments = responseResolver.toSuspiciousSegmentDtos(
                videoResult.getSuspiciousSegments()
        );
        if (suspiciousSegments.isEmpty() && !frameRisks.isEmpty()) {
            suspiciousSegments = suspiciousSegmentCalculator.compute(
                    frameRisks,
                    frameAnalysisProperties.getHighRiskFrameScoreThreshold(),
                    frameAnalysisProperties.getMinSuspiciousSegmentSec()
            );
        }

        moduleWriter.writeVideoAnalysisModules(
                analysisResultId,
                videoResult,
                response,
                response.getConfidenceScore(),
                frameRisks,
                suspiciousSegments
        );
    }

    private AnalysisResult buildResult(
            Long analysisRequestId,
            Double riskScore,
            Double confidenceScore,
            RiskLevel riskLevel,
            String summary,
            LocalDateTime analyzedAt
    ) {
        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(analysisRequestId);
        result.setRiskScore(riskScore);
        result.setConfidenceScore(confidenceScore);
        result.setRiskLevel(riskLevel);
        result.setSummary(summary);
        result.setAnalyzedAt(analyzedAt);
        return result;
    }

    private AnalysisResponseMessage.AnalysisVideoResultItem buildSimulatedVideoResultItem(
            List<FrameRiskDto> frameRisks,
            List<SuspiciousSegmentDto> suspiciousSegments
    ) {
        return AnalysisResponseMessage.AnalysisVideoResultItem.builder()
                .type("video")
                .lipSyncDetected(true)
                .lipSyncScore(0.78)
                .frameEditDetected(true)
                .frameEditScore(0.82)
                .deepfakeDetected(true)
                .deepfakeScore(0.88)
                .splicingDetected(false)
                .splicingScore(0.12)
                .reEncodingDetected(false)
                .reEncodingScore(0.05)
                .frameRisks(frameRisks.stream().map(responseResolver::toFrameRiskItem).toList())
                .suspiciousSegments(suspiciousSegments.stream().map(responseResolver::toSuspiciousSegmentItem).toList())
                .modelName("forenshield-video-mvp")
                .modelVersion("local-1.0")
                .build();
    }

    private String buildSummary(AnalysisResponseMessage response) {
        if (response.getAnalysisReasons() == null || response.getAnalysisReasons().isEmpty()) {
            return "AI analysis completed.";
        }
        return response.getAnalysisReasons().stream().collect(Collectors.joining(" "));
    }

    private RiskLevel parseRiskLevel(String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.valueOf(riskLevel.trim().toUpperCase());
    }

    private List<FrameRiskDto> buildSimulatedFrameRisks() {
        List<FrameRiskDto> risks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            double timestamp = i * frameAnalysisProperties.getExtractionIntervalSec();
            double score = (timestamp >= 12.0 && timestamp <= 15.0) ? 0.82 : 0.25;
            risks.add(FrameRiskDto.builder()
                    .frameIndex(i)
                    .timestampSec(timestamp)
                    .riskScore(score)
                    .build());
        }
        return risks;
    }
}
