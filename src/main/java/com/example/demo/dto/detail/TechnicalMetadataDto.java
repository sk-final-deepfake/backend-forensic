package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TechnicalMetadataDto {

    private Integer width;
    private Integer height;
    private Double durationSec;
    private Double fps;
    private String codec;
    private String extractionStatus;
}
