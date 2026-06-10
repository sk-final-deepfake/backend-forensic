package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EvidenceStatsResponse {

    private long imageCount;
    private long videoCount;
    private long audioCount;
}
