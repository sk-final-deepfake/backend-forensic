package com.example.demo.service;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.dto.AnalysisResponseMessage;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.util.ApiDateTimeFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalysisResultPersistenceService {

    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisModuleResultRepository analysisModuleResultRepository;
    private final ObjectMapper objectMapper;

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
            saveVideoModuleFromAi(savedResult.getAnalysisResultId(), videoResult, response.getConfidenceScore());
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

        saveVideoModule(savedResult.getAnalysisResultId(), true, 0.78, true, 0.82,
                true, 0.88, false, 0.12, false, 0.05);
        return savedResult.getAnalysisResultId();
    }

    private void saveVideoModuleFromAi(
            Long analysisResultId,
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            Double confidenceScore
    ) {
        AnalysisModuleResult module = new AnalysisModuleResult();
        module.setAnalysisResultId(analysisResultId);
        module.setFileType(FileType.VIDEO);
        module.setModuleName("video_analysis");
        module.setDetected(Boolean.TRUE.equals(videoResult.getDeepfakeDetected())
                || Boolean.TRUE.equals(videoResult.getLipSyncDetected())
                || Boolean.TRUE.equals(videoResult.getFrameEditDetected()));
        module.setScore(defaultDouble(videoResult.getDeepfakeScore()));
        module.setConfidence(confidenceScore == null ? 0.0 : confidenceScore);
        module.setModelName("forenshield-video-ai");
        module.setModelVersion("external");
        module.setEvidenceText("AI worker response");
        module.setDetailsJson(toJson(buildVideoDetails(
                Boolean.TRUE.equals(videoResult.getLipSyncDetected()), defaultDouble(videoResult.getLipSyncScore()),
                Boolean.TRUE.equals(videoResult.getFrameEditDetected()), defaultDouble(videoResult.getFrameEditScore()),
                Boolean.TRUE.equals(videoResult.getDeepfakeDetected()), defaultDouble(videoResult.getDeepfakeScore()),
                Boolean.TRUE.equals(videoResult.getSplicingDetected()), defaultDouble(videoResult.getSplicingScore()),
                Boolean.TRUE.equals(videoResult.getReEncodingDetected()), defaultDouble(videoResult.getReEncodingScore())
        )));
        module.setCreatedAt(LocalDateTime.now());
        analysisModuleResultRepository.save(module);
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

    private void saveVideoModule(
            Long analysisResultId,
            boolean lipSyncDetected, double lipSyncScore,
            boolean frameEditDetected, double frameEditScore,
            boolean deepfakeDetected, double deepfakeScore,
            boolean splicingDetected, double splicingScore,
            boolean reEncodingDetected, double reEncodingScore
    ) {
        AnalysisModuleResult module = new AnalysisModuleResult();
        module.setAnalysisResultId(analysisResultId);
        module.setFileType(FileType.VIDEO);
        module.setModuleName("video_analysis");
        module.setDetected(deepfakeDetected || lipSyncDetected || frameEditDetected);
        module.setScore(deepfakeScore);
        module.setConfidence(0.91);
        module.setModelName("forenshield-video-mvp");
        module.setModelVersion("local-1.0");
        module.setEvidenceText("Simulated local worker output");
        module.setDetailsJson(toJson(buildVideoDetails(
                lipSyncDetected, lipSyncScore,
                frameEditDetected, frameEditScore,
                deepfakeDetected, deepfakeScore,
                splicingDetected, splicingScore,
                reEncodingDetected, reEncodingScore
        )));
        module.setCreatedAt(LocalDateTime.now());
        analysisModuleResultRepository.save(module);
    }

    private Map<String, Object> buildVideoDetails(
            boolean lipSyncDetected, double lipSyncScore,
            boolean frameEditDetected, double frameEditScore,
            boolean deepfakeDetected, double deepfakeScore,
            boolean splicingDetected, double splicingScore,
            boolean reEncodingDetected, double reEncodingScore
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
        details.put("reEncodingDetected", reEncodingDetected);
        details.put("reEncodingScore", reEncodingScore);
        return details;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize module details", ex);
        }
    }
}
