package com.example.demo.service;

import com.example.demo.config.VideoFrameAnalysisProperties;
import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.dto.AnalysisResponseMessage;
import com.example.demo.dto.FrameRiskDto;
import com.example.demo.dto.SuspiciousSegmentDto;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.util.ApiDateTimeFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisResultPersistenceService {

    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisModuleResultRepository analysisModuleResultRepository;
    private final ObjectMapper objectMapper;
    private final SuspiciousSegmentCalculator suspiciousSegmentCalculator;
    private final VideoFrameAnalysisProperties frameAnalysisProperties;

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

        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(request.getAnalysisRequestId());
        result.setRiskScore(response.getRiskScore());
        result.setConfidenceScore(response.getConfidenceScore());
        result.setRiskLevel(parseRiskLevel(response.getRiskLevel()));
        result.setSummary(buildSummary(response));
        result.setAnalyzedAt(ApiDateTimeFormatter.parseUtc(response.getAnalyzedAt()));
        AnalysisResult savedResult = analysisResultRepository.save(result);

        AnalysisResponseMessage.AnalysisVideoResultItem videoResult = findVideoResult(response.getResults());
        if (videoResult != null) {
            saveVideoAnalysisModules(
                    savedResult.getAnalysisResultId(),
                    videoResult,
                    response,
                    response.getConfidenceScore()
            );
        }
        return savedResult.getAnalysisResultId();
    }

    private Long createSimulatedVideoResult(Long analysisRequestId) {
        analysisRequestRepository.findById(analysisRequestId)
                .orElseThrow(() -> new IllegalStateException("AnalysisRequest not found: " + analysisRequestId));

        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(analysisRequestId);
        result.setRiskScore(72.5);
        result.setConfidenceScore(0.91);
        result.setRiskLevel(RiskLevel.HIGH);
        result.setSummary("Local worker: simulated video analysis completed (see ai-json.md).");
        result.setAnalyzedAt(LocalDateTime.now());
        AnalysisResult savedResult = analysisResultRepository.save(result);

        List<FrameRiskDto> simulatedFrameRisks = buildSimulatedFrameRisks();
        List<SuspiciousSegmentDto> simulatedSegments = suspiciousSegmentCalculator.compute(
                simulatedFrameRisks,
                frameAnalysisProperties.getHighRiskFrameScoreThreshold(),
                frameAnalysisProperties.getMinSuspiciousSegmentSec()
        );

        saveVideoAnalysisModules(
                savedResult.getAnalysisResultId(),
                buildSimulatedVideoResultItem(simulatedFrameRisks, simulatedSegments),
                null,
                0.91
        );
        return savedResult.getAnalysisResultId();
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
                .frameRisks(frameRisks.stream().map(this::toFrameRiskItem).toList())
                .suspiciousSegments(suspiciousSegments.stream().map(this::toSuspiciousSegmentItem).toList())
                .modelName("forenshield-video-mvp")
                .modelVersion("local-1.0")
                .build();
    }

    private void saveVideoAnalysisModules(
            Long analysisResultId,
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            AnalysisResponseMessage response,
            Double confidenceScore
    ) {
        List<FrameRiskDto> frameRisks = toFrameRiskDtos(videoResult.getFrameRisks());
        List<SuspiciousSegmentDto> suspiciousSegments = toSuspiciousSegmentDtos(videoResult.getSuspiciousSegments());
        if (suspiciousSegments.isEmpty() && !frameRisks.isEmpty()) {
            suspiciousSegments = suspiciousSegmentCalculator.compute(
                    frameRisks,
                    frameAnalysisProperties.getHighRiskFrameScoreThreshold(),
                    frameAnalysisProperties.getMinSuspiciousSegmentSec()
            );
        }

        List<String> evidenceItems = resolveEvidenceItems(videoResult, response);
        String defaultModelName = resolveModelName(videoResult, response);
        String defaultModelVersion = resolveModelVersion(videoResult, response);
        double confidence = confidenceScore == null ? 0.0 : confidenceScore;

        List<AnalysisResponseMessage.ModelScoreItem> explicitScores = resolveModelScores(videoResult, response);
        if (explicitScores.isEmpty()) {
            saveDetectionModule(analysisResultId, "deepfake", videoResult.getDeepfakeDetected(),
                    videoResult.getDeepfakeScore(), confidence, defaultModelName, defaultModelVersion, null);
            saveDetectionModule(analysisResultId, "lip_sync", videoResult.getLipSyncDetected(),
                    videoResult.getLipSyncScore(), confidence, defaultModelName, defaultModelVersion, null);
            saveDetectionModule(analysisResultId, "frame_edit", videoResult.getFrameEditDetected(),
                    videoResult.getFrameEditScore(), confidence, defaultModelName, defaultModelVersion, null);
            saveDetectionModule(analysisResultId, "splicing", videoResult.getSplicingDetected(),
                    videoResult.getSplicingScore(), confidence, defaultModelName, defaultModelVersion, null);
            saveDetectionModule(analysisResultId, "re_encoding", videoResult.getReEncodingDetected(),
                    videoResult.getReEncodingScore(), confidence, defaultModelName, defaultModelVersion, null);
        } else {
            for (AnalysisResponseMessage.ModelScoreItem scoreItem : explicitScores) {
                saveDetectionModule(
                        analysisResultId,
                        scoreItem.getModuleName(),
                        scoreItem.getDetected(),
                        scoreItem.getScore(),
                        confidence,
                        scoreItem.getModelName() != null ? scoreItem.getModelName() : defaultModelName,
                        scoreItem.getModelVersion() != null ? scoreItem.getModelVersion() : defaultModelVersion,
                        null
                );
            }
        }

        saveTimelineModule(
                analysisResultId,
                videoResult,
                frameRisks,
                suspiciousSegments,
                evidenceItems
        );
    }

    private void saveDetectionModule(
            Long analysisResultId,
            String moduleName,
            Boolean detected,
            Double score,
            double confidence,
            String modelName,
            String modelVersion,
            String evidenceText
    ) {
        AnalysisModuleResult module = new AnalysisModuleResult();
        module.setAnalysisResultId(analysisResultId);
        module.setFileType(FileType.VIDEO);
        module.setModuleName(moduleName);
        module.setDetected(Boolean.TRUE.equals(detected));
        module.setScore(defaultDouble(score));
        module.setConfidence(confidence);
        module.setModelName(modelName);
        module.setModelVersion(modelVersion);
        module.setEvidenceText(evidenceText);
        module.setDetailsJson(toJson(Map.of("type", "video", "moduleName", moduleName)));
        module.setCreatedAt(LocalDateTime.now());
        analysisModuleResultRepository.save(module);
    }

    private void saveTimelineModule(
            Long analysisResultId,
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            List<FrameRiskDto> frameRisks,
            List<SuspiciousSegmentDto> suspiciousSegments,
            List<String> evidenceItems
    ) {
        AnalysisModuleResult module = new AnalysisModuleResult();
        module.setAnalysisResultId(analysisResultId);
        module.setFileType(FileType.VIDEO);
        module.setModuleName("video_timeline");
        module.setDetected(!frameRisks.isEmpty() || !suspiciousSegments.isEmpty());
        module.setScore(0.0);
        module.setConfidence(0.0);
        module.setModelName(resolveModelName(videoResult, null));
        module.setModelVersion(resolveModelVersion(videoResult, null));
        module.setEvidenceText(null);
        module.setDetailsJson(toJson(buildVideoDetails(
                Boolean.TRUE.equals(videoResult.getLipSyncDetected()), defaultDouble(videoResult.getLipSyncScore()),
                Boolean.TRUE.equals(videoResult.getFrameEditDetected()), defaultDouble(videoResult.getFrameEditScore()),
                Boolean.TRUE.equals(videoResult.getDeepfakeDetected()), defaultDouble(videoResult.getDeepfakeScore()),
                Boolean.TRUE.equals(videoResult.getSplicingDetected()), defaultDouble(videoResult.getSplicingScore()),
                Boolean.TRUE.equals(videoResult.getReEncodingDetected()), defaultDouble(videoResult.getReEncodingScore()),
                frameRisks,
                suspiciousSegments,
                evidenceItems
        )));
        module.setCreatedAt(LocalDateTime.now());
        analysisModuleResultRepository.save(module);
    }

    private List<String> resolveEvidenceItems(
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            AnalysisResponseMessage response
    ) {
        if (videoResult.getEvidence() != null && !videoResult.getEvidence().isEmpty()) {
            return videoResult.getEvidence();
        }
        if (response != null && response.getEvidence() != null && !response.getEvidence().isEmpty()) {
            return response.getEvidence();
        }
        if (response != null && response.getAnalysisReasons() != null && !response.getAnalysisReasons().isEmpty()) {
            return response.getAnalysisReasons();
        }
        return List.of();
    }

    private List<AnalysisResponseMessage.ModelScoreItem> resolveModelScores(
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            AnalysisResponseMessage response
    ) {
        if (videoResult.getModelScores() != null && !videoResult.getModelScores().isEmpty()) {
            return videoResult.getModelScores();
        }
        if (response != null && response.getModelScores() != null && !response.getModelScores().isEmpty()) {
            return response.getModelScores();
        }
        return List.of();
    }

    private String resolveModelName(
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            AnalysisResponseMessage response
    ) {
        if (videoResult.getModelName() != null && !videoResult.getModelName().isBlank()) {
            return videoResult.getModelName();
        }
        if (response != null && response.getModelName() != null && !response.getModelName().isBlank()) {
            return response.getModelName();
        }
        return "forenshield-video-ai";
    }

    private String resolveModelVersion(
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            AnalysisResponseMessage response
    ) {
        if (videoResult.getModelVersion() != null && !videoResult.getModelVersion().isBlank()) {
            return videoResult.getModelVersion();
        }
        if (response != null && response.getModelVersion() != null && !response.getModelVersion().isBlank()) {
            return response.getModelVersion();
        }
        return "external";
    }

    private AnalysisResponseMessage.AnalysisVideoResultItem findVideoResult(
            List<AnalysisResponseMessage.AnalysisVideoResultItem> results
    ) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        return results.stream()
                .filter(item -> item.getType() == null || "video".equalsIgnoreCase(item.getType()))
                .findFirst()
                .orElse(results.get(0));
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

    private double defaultDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private Map<String, Object> buildVideoDetails(
            boolean lipSyncDetected, double lipSyncScore,
            boolean frameEditDetected, double frameEditScore,
            boolean deepfakeDetected, double deepfakeScore,
            boolean splicingDetected, double splicingScore,
            boolean reEncodingDetected, double reEncodingScore,
            List<FrameRiskDto> frameRisks,
            List<SuspiciousSegmentDto> suspiciousSegments,
            List<String> analysisReasons
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", "video");
        details.put("lipSyncDetected", lipSyncDetected);
        details.put("lipSyncScore", lipSyncScore);
        details.put("frameEditDetected", frameEditDetected);
        details.put("frameEditScore", frameEditScore);
        details.put("deepfakeDetected", deepfakeDetected);
        details.put("deepfakeScore", deepfakeScore);
        details.put("splicingDetected", splicingDetected);
        details.put("splicingScore", splicingScore);
        details.put("videoEditDetected", splicingDetected);
        details.put("videoEditScore", splicingScore);
        details.put("reEncodingDetected", reEncodingDetected);
        details.put("reEncodingScore", reEncodingScore);
        details.put("highRiskFrameScoreThreshold", frameAnalysisProperties.getHighRiskFrameScoreThreshold());
        details.put("frameRisks", frameRisks == null ? List.of() : frameRisks);
        details.put("suspiciousSegments", suspiciousSegments == null ? List.of() : suspiciousSegments);
        details.put("analysisReasons", analysisReasons == null ? List.of() : analysisReasons);
        return details;
    }

    private AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem toFrameRiskItem(FrameRiskDto frameRisk) {
        return AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem.builder()
                .frameIndex(frameRisk.getFrameIndex())
                .timestampSec(frameRisk.getTimestampSec())
                .riskScore(frameRisk.getRiskScore())
                .build();
    }

    private AnalysisResponseMessage.AnalysisVideoResultItem.SuspiciousSegmentItem toSuspiciousSegmentItem(
            SuspiciousSegmentDto segment
    ) {
        return AnalysisResponseMessage.AnalysisVideoResultItem.SuspiciousSegmentItem.builder()
                .startTime(segment.getStartTime())
                .endTime(segment.getEndTime())
                .maxRiskScore(segment.getMaxRiskScore())
                .reason(segment.getReason())
                .build();
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

    private List<FrameRiskDto> toFrameRiskDtos(
            List<AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem> frameRisks
    ) {
        if (frameRisks == null || frameRisks.isEmpty()) {
            return List.of();
        }
        List<FrameRiskDto> converted = new ArrayList<>();
        for (int i = 0; i < frameRisks.size(); i++) {
            AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem item = frameRisks.get(i);
            converted.add(FrameRiskDto.builder()
                    .frameIndex(item.getFrameIndex() == null ? i : item.getFrameIndex())
                    .timestampSec(defaultDouble(item.getTimestampSec()))
                    .riskScore(defaultDouble(item.getRiskScore()))
                    .build());
        }
        return converted;
    }

    private List<SuspiciousSegmentDto> toSuspiciousSegmentDtos(
            List<AnalysisResponseMessage.AnalysisVideoResultItem.SuspiciousSegmentItem> segments
    ) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        return segments.stream()
                .map(item -> SuspiciousSegmentDto.builder()
                        .startTime(defaultDouble(item.getStartTime()))
                        .endTime(defaultDouble(item.getEndTime()))
                        .maxRiskScore(defaultDouble(item.getMaxRiskScore()))
                        .reason(item.getReason())
                        .build())
                .toList();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize module details", ex);
        }
    }
}
