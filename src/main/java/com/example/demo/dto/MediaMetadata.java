package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MediaMetadata {
    private String type; // "video", "audio", or "image"
    private Double duration;
    private String codec;
    
    // Video specific
    private Integer width;
    private Integer height;
    private Double fps;
    
    // Audio specific
    private Integer sampleRate;
    private Integer channels;
}
