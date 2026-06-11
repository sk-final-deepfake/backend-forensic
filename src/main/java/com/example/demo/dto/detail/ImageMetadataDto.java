package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class ImageMetadataDto {
    private Integer width;
    private Integer height;
    private String deviceInfo;
    private LocalDateTime capturedAt;
}
