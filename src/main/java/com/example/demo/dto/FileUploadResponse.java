package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileUploadResponse {
    private boolean success;
    private String message;
    private String fileName;
    private Long fileSize;
    private MediaMetadata metadata;
}
