package com.example.demo.service;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.enums.FileType;
import com.example.demo.dto.AnalysisResponseMessage;
import com.example.demo.dto.FrameRiskDto;
import com.example.demo.dto.SuspiciousSegmentDto;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoAnalysisModuleWriter {

    private static final List<DetectionSpec> DEFAULT_DETECTIONS = List.of(
            new DetectionSpec("deepfake",
                    AnalysisResponseMessage.AnalysisVideoResultItem::getDeepfakeDetected,
                    AnalysisResponseMessage.AnalysisVideoResultItem::getDeepfakeScore),
            new DetectionSpec("lip_sync",
                    AnalysisResponseMessage.AnalysisVideoResultItem::getLipSyncDetected,
                    AnalysisResponseMessage.AnalysisVideoResultItem::getLipSyncScore),
            new DetectionSpec("frame_edit",
                    AnalysisResponseMessage.AnalysisVideoResultItem::getFrameEditDetected,
                    AnalysisResponseMessage.AnalysisVideoResultItem::getFrameEditScore),
            new DetectionSpec("splicing",
                    AnalysisResponseMessage.AnalysisVideoResultItem::getSplicingDetected,
                    AnalysisResponseMessage.AnalysisVideoResultItem::getSplicingScore),
            new DetectionSpec("re_encoding",
                    AnalysisResponseMessage.AnalysisVideoResultItem::getReEncodingDetected,
                    AnalysisResponseMessage.AnalysisVideoResultItem::getReEncodingScore)
    );

    private final AnalysisModuleResultRepository analysisModuleResultRepository;
    private final AnalysisResponseResolver responseResolver;
    private final VideoAnalysisDetailsBuilder detailsBuilder;
    private final ObjectMapper objectMapper;

    public void writeVideoAnalysisModules(
            Long analysisResultId,
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            AnalysisResponseMessage response,
            Double confidenceScore,
            List<FrameRiskDto> frameRisks,
            List<SuspiciousSegmentDto> suspiciousSegments
    ) {
        List<String> evidenceItems = responseResolver.resolveEvidenceItems(videoResult, response);
        String defaultModelName = responseResolver.resolveModelName(videoResult, response);
        String defaultModelVersion = responseResolver.resolveModelVersion(videoResult, response);
        double confidence = confidenceScore == null ? 0.0 : confidenceScore;

        List<AnalysisResponseMessage.ModelScoreItem> explicitScores = responseResolver.resolveModelScores(
                videoResult,
                response
        );
        if (explicitScores.isEmpty()) {
            for (DetectionSpec spec : DEFAULT_DETECTIONS) {
                saveDetectionModule(
                        analysisResultId,
                        spec.moduleName(),
                        spec.detected().apply(videoResult),
                        spec.score().apply(videoResult),
                        confidence,
                        defaultModelName,
                        defaultModelVersion
                );
            }
        } else {
            for (AnalysisResponseMessage.ModelScoreItem scoreItem : explicitScores) {
                saveDetectionModule(
                        analysisResultId,
                        scoreItem.getModuleName(),
                        scoreItem.getDetected(),
                        scoreItem.getScore(),
                        confidence,
                        scoreItem.getModelName() != null ? scoreItem.getModelName() : defaultModelName,
                        scoreItem.getModelVersion() != null ? scoreItem.getModelVersion() : defaultModelVersion
                );
            }
        }

        saveTimelineModule(analysisResultId, videoResult, frameRisks, suspiciousSegments, evidenceItems);
    }

    private void saveDetectionModule(
            Long analysisResultId,
            String moduleName,
            Boolean detected,
            Double score,
            double confidence,
            String modelName,
            String modelVersion
    ) {
        AnalysisModuleResult module = new AnalysisModuleResult();
        module.setAnalysisResultId(analysisResultId);
        module.setFileType(FileType.VIDEO);
        module.setModuleName(moduleName);
        module.setDetected(Boolean.TRUE.equals(detected));
        module.setScore(responseResolver.defaultDouble(score));
        module.setConfidence(confidence);
        module.setModelName(modelName);
        module.setModelVersion(modelVersion);
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
        module.setModuleName(VideoAnalysisDetailsBuilder.MODULE_VIDEO_TIMELINE);
        module.setDetected(!frameRisks.isEmpty() || !suspiciousSegments.isEmpty());
        module.setScore(0.0);
        module.setConfidence(0.0);
        module.setModelName(responseResolver.resolveModelName(videoResult, null));
        module.setModelVersion(responseResolver.resolveModelVersion(videoResult, null));
        module.setDetailsJson(toJson(detailsBuilder.buildTimelineDetails(
                videoResult,
                frameRisks,
                suspiciousSegments,
                evidenceItems
        )));
        module.setCreatedAt(LocalDateTime.now());
        analysisModuleResultRepository.save(module);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize module details", ex);
        }
    }

    private record DetectionSpec(
            String moduleName,
            Function<AnalysisResponseMessage.AnalysisVideoResultItem, Boolean> detected,
            Function<AnalysisResponseMessage.AnalysisVideoResultItem, Double> score
    ) {
    }
}
