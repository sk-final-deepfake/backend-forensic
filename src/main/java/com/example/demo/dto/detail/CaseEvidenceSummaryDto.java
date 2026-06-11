package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CaseEvidenceSummaryDto {

    private Long evidenceId;
    private String fileName;
    private String mediaType;
    private String analysisStatus;
}
