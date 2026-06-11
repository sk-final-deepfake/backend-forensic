package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AnalysisInfoDto {

    private String status;
    private String requestedAt;
    private String completedAt;
    private Double riskScore;
    private Double confidenceScore;
    private String riskLevel;
    private String summary;
    private List<ModuleResultDto> moduleResults;
}
