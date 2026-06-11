package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class EvidenceDetailResponse {

    private EvidenceInfoDto evidenceInfo;
    private IntegrityInfoDto integrityInfo;
    private AnalysisInfoDto analysisInfo;
    private List<CocLogDto> cocLogs;
}
