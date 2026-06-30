package com.example.demo.service.custody;

import com.example.demo.domain.Report;
import com.example.demo.domain.enums.CustodyTargetType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportCustodyLogService {

    private final CustodyLogService custodyLogService;
    private final ObjectMapper objectMapper;

    public void recordReportCreated(Long actorId, Report report) {
        custodyLogService.record(
                actorId,
                CustodyTargetType.REPORT,
                report.getReportId(),
                "REPORT_CREATED",
                report.getReportHash(),
                report.getStoragePath(),
                "포렌식 PDF 리포트 생성",
                toJson(reportPayload(report, "CREATED")),
                null
        );
    }

    public void recordReportDownloaded(Long actorId, Report report) {
        custodyLogService.record(
                actorId,
                CustodyTargetType.REPORT,
                report.getReportId(),
                "REPORT_DOWNLOADED",
                report.getReportHash(),
                report.getStoragePath(),
                "포렌식 PDF 리포트 다운로드",
                toJson(reportPayload(report, "DOWNLOADED")),
                null
        );
    }

    private Map<String, Object> reportPayload(Report report, String action) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", report.getReportId());
        payload.put("evidenceId", report.getEvidenceId());
        payload.put("action", action);
        payload.put("reportFileName", report.getReportFileName());
        payload.put("reportHash", report.getReportHash());
        payload.put("fileSize", report.getFileSize());
        if (report.getAnalysisResultId() != null) {
            payload.put("analysisResultId", report.getAnalysisResultId());
        }
        if (report.getCompareId() != null) {
            payload.put("compareId", report.getCompareId());
        }
        return payload;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize report custody payload", ex);
        }
    }
}
