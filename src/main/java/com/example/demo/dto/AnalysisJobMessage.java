package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisJobMessage {

    private Long analysisRequestId;
    private Long evidenceId;
    @JsonProperty("fileType")
    private String fileType;
    private String filePath;
    private String originalHash;
    private String caseName;
    private String requestedAt;
}
