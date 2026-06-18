package com.example.demo.service;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local/dev worker 및 AI 응답 수신 시 {@link AnalysisResult}·{@link AnalysisModuleResult} 영속화.
 * ai-json.md §3~4 기준.
 */
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

    private Long createSimulatedVideoResult(Long analysisRequestId) {
        AnalysisRequest request = analysisRequestRepository.findById(analysisRequestId)
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
