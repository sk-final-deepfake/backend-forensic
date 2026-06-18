package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

/** RQ-DTL-075: Evidence Manifest 요약 */
@Getter
@Builder
public class ManifestInfoDto {

    private Long evidenceId;
    private String caseNumber;
    private String caseName;
    private String fileName;
    private String hashAlgorithm;
    private String originalHash;
    private String copyHash;
    /** ISO-8601 UTC */
    private String manifestCreatedAt;
    private String manifestHash;
    private String issuer;
}
