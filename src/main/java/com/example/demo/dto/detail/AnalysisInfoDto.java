package com.example.demo.dto.detail;

import com.example.demo.dto.FrameRiskDto;
import com.example.demo.dto.SuspiciousSegmentDto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AnalysisInfoDto {

    private String status;
    /** SK-923: WAITING · ANALYZING · COMPLETED · FAILED */
    private String queueStatus;
    private Long analysisRequestId;
    private String requestedAt;
    private String completedAt;
    private Double riskScore;
    private Double confidenceScore;
    private String riskLevel;
    private String summary;
    private boolean completed;
    /** SK-951: FAILED일 때만 */
    private String errorCode;
    private String errorMessage;
    private List<ModuleResultDto> moduleResults;
    /** SK-949 */
    private List<ModelScoreDto> modelScores;
    /** SK-950 */
    private List<String> evidenceItems;
    /** SK-943~945, SK-458~459 */
    private List<FrameRiskDto> frameRisks;
    private List<SuspiciousSegmentDto> suspiciousSegments;
}
