package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StartAnalysisResponse {

    private boolean success;
    private String message;
    private String caseName;
    private int startedCount;
    private List<Long> evidenceIds;
}
