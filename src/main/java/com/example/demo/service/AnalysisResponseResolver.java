package com.example.demo.service;

import com.example.demo.dto.AnalysisResponseMessage;
import com.example.demo.dto.FrameRiskDto;
import com.example.demo.dto.SuspiciousSegmentDto;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnalysisResponseResolver {

    static final String DEFAULT_MODEL_NAME = "forenshield-video-ai";
    static final String DEFAULT_MODEL_VERSION = "external";

    public AnalysisResponseMessage.AnalysisVideoResultItem findVideoResult(
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

    public List<String> resolveEvidenceItems(
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

    public List<AnalysisResponseMessage.ModelScoreItem> resolveModelScores(
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

    public String resolveModelName(
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            AnalysisResponseMessage response
    ) {
        if (videoResult.getModelName() != null && !videoResult.getModelName().isBlank()) {
            return videoResult.getModelName();
        }
        if (response != null && response.getModelName() != null && !response.getModelName().isBlank()) {
            return response.getModelName();
        }
        return DEFAULT_MODEL_NAME;
    }

    public String resolveModelVersion(
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            AnalysisResponseMessage response
    ) {
        if (videoResult.getModelVersion() != null && !videoResult.getModelVersion().isBlank()) {
            return videoResult.getModelVersion();
        }
        if (response != null && response.getModelVersion() != null && !response.getModelVersion().isBlank()) {
            return response.getModelVersion();
        }
        return DEFAULT_MODEL_VERSION;
    }

    public List<FrameRiskDto> toFrameRiskDtos(
            List<AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem> frameRisks
    ) {
        if (frameRisks == null || frameRisks.isEmpty()) {
            return List.of();
        }
        List<FrameRiskDto> converted = new java.util.ArrayList<>();
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

    public List<SuspiciousSegmentDto> toSuspiciousSegmentDtos(
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

    public AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem toFrameRiskItem(FrameRiskDto frameRisk) {
        return AnalysisResponseMessage.AnalysisVideoResultItem.FrameRiskItem.builder()
                .frameIndex(frameRisk.getFrameIndex())
                .timestampSec(frameRisk.getTimestampSec())
                .riskScore(frameRisk.getRiskScore())
                .build();
    }

    public AnalysisResponseMessage.AnalysisVideoResultItem.SuspiciousSegmentItem toSuspiciousSegmentItem(
            SuspiciousSegmentDto segment
    ) {
        return AnalysisResponseMessage.AnalysisVideoResultItem.SuspiciousSegmentItem.builder()
                .startTime(segment.getStartTime())
                .endTime(segment.getEndTime())
                .maxRiskScore(segment.getMaxRiskScore())
                .reason(segment.getReason())
                .build();
    }

    public double defaultDouble(Double value) {
        return value == null ? 0.0 : value;
    }
}
