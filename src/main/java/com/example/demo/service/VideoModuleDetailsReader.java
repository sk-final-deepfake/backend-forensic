package com.example.demo.service;

import com.example.demo.dto.FrameRiskDto;
import com.example.demo.dto.SuspiciousSegmentDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoModuleDetailsReader {

    private final ObjectMapper objectMapper;

    public Map<String, Object> parse(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(detailsJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<FrameRiskDto> readFrameRisks(Map<String, Object> details) {
        Object raw = details.get("frameRisks");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> map = (Map<String, Object>) item;
                    return FrameRiskDto.builder()
                            .frameIndex(asInt(map.get("frameIndex")))
                            .timestampSec(asDouble(map.get("timestampSec")))
                            .riskScore(asDouble(map.get("riskScore")))
                            .build();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<SuspiciousSegmentDto> readSuspiciousSegments(Map<String, Object> details) {
        Object raw = details.get("suspiciousSegments");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> {
                    Map<String, Object> map = (Map<String, Object>) item;
                    return SuspiciousSegmentDto.builder()
                            .startTime(asDouble(map.get("startTime")))
                            .endTime(asDouble(map.get("endTime")))
                            .maxRiskScore(asDouble(map.get("maxRiskScore")))
                            .reason(asString(map.get("reason")))
                            .build();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<String> readEvidenceItems(Map<String, Object> details) {
        Object raw = details.get("analysisReasons");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
