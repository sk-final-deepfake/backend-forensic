package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EvidenceInfoDto {

    private Long evidenceId;
    private String fileName;
    private String caseName;
    private Long fileSize;
    private String uploadedAt;
    private String mediaType;
    private String fileType;
    private VideoMetadataDto technicalMetadata;
}
