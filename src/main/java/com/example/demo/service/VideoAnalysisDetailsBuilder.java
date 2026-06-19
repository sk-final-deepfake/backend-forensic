package com.example.demo.service;

import com.example.demo.config.VideoFrameAnalysisProperties;
import com.example.demo.dto.AnalysisResponseMessage;
import com.example.demo.dto.FrameRiskDto;
import com.example.demo.dto.SuspiciousSegmentDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoAnalysisDetailsBuilder {

    static final String MODULE_VIDEO_TIMELINE = "video_timeline";

    private final VideoFrameAnalysisProperties frameAnalysisProperties;
    private final AnalysisResponseResolver responseResolver;

    public Map<String, Object> buildTimelineDetails(
            AnalysisResponseMessage.AnalysisVideoResultItem videoResult,
            List<FrameRiskDto> frameRisks,
            List<SuspiciousSegmentDto> suspiciousSegments,
            List<String> evidenceItems
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", "video");
        putDetection(details, "lipSync", videoResult.getLipSyncDetected(), videoResult.getLipSyncScore());
        putDetection(details, "frameEdit", videoResult.getFrameEditDetected(), videoResult.getFrameEditScore());
        putDetection(details, "deepfake", videoResult.getDeepfakeDetected(), videoResult.getDeepfakeScore());
        putDetection(details, "splicing", videoResult.getSplicingDetected(), videoResult.getSplicingScore());
        details.put("videoEditDetected", Boolean.TRUE.equals(videoResult.getSplicingDetected()));
        details.put("videoEditScore", responseResolver.defaultDouble(videoResult.getSplicingScore()));
        putDetection(details, "reEncoding", videoResult.getReEncodingDetected(), videoResult.getReEncodingScore());
        details.put("highRiskFrameScoreThreshold", frameAnalysisProperties.getHighRiskFrameScoreThreshold());
        details.put("frameRisks", frameRisks == null ? List.of() : frameRisks);
        details.put("suspiciousSegments", suspiciousSegments == null ? List.of() : suspiciousSegments);
        details.put("analysisReasons", evidenceItems == null ? List.of() : evidenceItems);
        return details;
    }

    private void putDetection(Map<String, Object> details, String prefix, Boolean detected, Double score) {
        details.put(prefix + "Detected", Boolean.TRUE.equals(detected));
        details.put(prefix + "Score", responseResolver.defaultDouble(score));
    }
}
