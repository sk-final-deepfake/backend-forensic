package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AnalysisStatusResponse {

    private Long evidenceId;
    private Long analysisRequestId;
    private String status;
    private int progressPercent;
}
