package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisResponseMessage {

    private Long analysisRequestId;
    private Long evidenceId;
    private String status;
    private Double riskScore;
    private Double confidenceScore;
    private String riskLevel;
    private List<String> analysisReasons;
    private List<AnalysisVideoResultItem> results;
    private String analyzedAt;
    private String errorCode;
    private String message;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnalysisVideoResultItem {

        private String type;
        private Boolean lipSyncDetected;
        private Double lipSyncScore;
        private Boolean frameEditDetected;
        private Double frameEditScore;
        private Boolean deepfakeDetected;
        private Double deepfakeScore;
        private Boolean splicingDetected;
        private Double splicingScore;
        private Boolean reEncodingDetected;
        private Double reEncodingScore;
    }
}
