package com.example.demo.service.custody;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Report;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.ReportRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceCustodyTimelineServiceTest {

    @Mock
    private CustodyLogRepository custodyLogRepository;
    @Mock
    private AnalysisRequestRepository analysisRequestRepository;
    @Mock
    private AnalysisResultRepository analysisResultRepository;
    @Mock
    private ReportRepository reportRepository;

    private EvidenceCustodyTimelineService timelineService;

    @BeforeEach
    void setUp() {
        timelineService = new EvidenceCustodyTimelineService(
                custodyLogRepository,
                analysisRequestRepository,
                analysisResultRepository,
                reportRepository
        );
    }

    @Test
    @DisplayName("증거 상세 타임라인에 업로드·해시·분석요청·분석완료·보고서생성 로그를 모두 포함한다")
    void loadTimelineAsc_aggregatesEvidenceAnalysisAndReportLogs() {
        Long evidenceId = 10L;
        Long requestId = 20L;
        Long resultId = 30L;
        Long reportId = 40L;

        CustodyLog uploaded = log(1L, CustodyTargetType.EVIDENCE, evidenceId, "EVIDENCE_UPLOADED",
                LocalDateTime.of(2026, 7, 20, 10, 0));
        CustodyLog hashCreated = log(2L, CustodyTargetType.EVIDENCE, evidenceId, "HASH_CREATED",
                LocalDateTime.of(2026, 7, 20, 10, 0, 1));
        CustodyLog requested = log(3L, CustodyTargetType.ANALYSIS_REQUEST, requestId, "ANALYSIS_REQUESTED",
                LocalDateTime.of(2026, 7, 20, 10, 5));
        CustodyLog completed = log(4L, CustodyTargetType.ANALYSIS_RESULT, resultId, "ANALYSIS_COMPLETED",
                LocalDateTime.of(2026, 7, 20, 10, 30));
        CustodyLog reportCreated = log(5L, CustodyTargetType.REPORT, reportId, "REPORT_CREATED",
                LocalDateTime.of(2026, 7, 20, 11, 0));

        AnalysisRequest request = new AnalysisRequest();
        request.setAnalysisRequestId(requestId);
        request.setEvidenceId(evidenceId);

        AnalysisResult result = new AnalysisResult();
        result.setAnalysisResultId(resultId);
        result.setAnalysisRequestId(requestId);

        Report report = new Report();
        report.setReportId(reportId);
        report.setEvidenceId(evidenceId);

        when(custodyLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                CustodyTargetType.EVIDENCE, evidenceId
        )).thenReturn(List.of(uploaded, hashCreated));
        when(analysisRequestRepository.findByEvidenceIdOrderByRequestedAtDesc(evidenceId))
                .thenReturn(List.of(request));
        when(custodyLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                CustodyTargetType.ANALYSIS_REQUEST, requestId
        )).thenReturn(List.of(requested));
        when(analysisResultRepository.findByAnalysisRequestIdIn(List.of(requestId)))
                .thenReturn(List.of(result));
        when(custodyLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                CustodyTargetType.ANALYSIS_RESULT, resultId
        )).thenReturn(List.of(completed));
        when(reportRepository.findByEvidenceIdOrderByCreatedAtDesc(evidenceId))
                .thenReturn(List.of(report));
        when(custodyLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                CustodyTargetType.REPORT, reportId
        )).thenReturn(List.of(reportCreated));

        List<CustodyLog> timeline = timelineService.loadTimelineAsc(evidenceId);

        assertThat(timeline)
                .extracting(CustodyLog::getActionType)
                .containsExactly(
                        "EVIDENCE_UPLOADED",
                        "HASH_CREATED",
                        "ANALYSIS_REQUESTED",
                        "ANALYSIS_COMPLETED",
                        "REPORT_CREATED"
                );
    }

    private static CustodyLog log(
            Long logId,
            CustodyTargetType targetType,
            Long targetId,
            String actionType,
            LocalDateTime createdAt
    ) {
        CustodyLog log = new CustodyLog();
        log.setLogId(logId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setActionType(actionType);
        log.setCreatedAt(createdAt);
        log.setActorId(1L);
        log.setCurrentLogHash("a".repeat(64));
        return log;
    }
}
