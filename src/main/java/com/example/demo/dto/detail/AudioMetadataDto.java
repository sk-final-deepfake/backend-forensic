package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AudioMetadataDto {
    private Double durationSec;
    private Integer sampleRate;
    private Integer channels;
    private String codec;
}
