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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 증거 상세 CoC 타임라인용 로그 집계.
 *
 * <p>CoC는 타겟별로 해시체인을 유지하므로 기록은 EVIDENCE / ANALYSIS_REQUEST /
 * ANALYSIS_RESULT / REPORT 에 분산된다. 상세 UI는 증거 단위로 한 타임라인을
 * 보여주므로, 조회 시 관련 타겟 로그를 합친다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceCustodyTimelineService {

    private final CustodyLogRepository custodyLogRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final ReportRepository reportRepository;

    public List<CustodyLog> loadTimelineAsc(Long evidenceId) {
        return sort(collect(evidenceId), true);
    }

    public List<CustodyLog> loadTimelineDesc(Long evidenceId) {
        return sort(collect(evidenceId), false);
    }

    private List<CustodyLog> collect(Long evidenceId) {
        List<CustodyLog> logs = new ArrayList<>(custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId));

        List<AnalysisRequest> requests =
                analysisRequestRepository.findByEvidenceIdOrderByRequestedAtDesc(evidenceId);
        List<Long> requestIds = requests.stream()
                .map(AnalysisRequest::getAnalysisRequestId)
                .filter(Objects::nonNull)
                .toList();
        for (Long requestId : requestIds) {
            logs.addAll(custodyLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                    CustodyTargetType.ANALYSIS_REQUEST,
                    requestId
            ));
        }

        if (!requestIds.isEmpty()) {
            List<AnalysisResult> results = analysisResultRepository.findByAnalysisRequestIdIn(requestIds);
            for (AnalysisResult result : results) {
                if (result.getAnalysisResultId() == null) {
                    continue;
                }
                logs.addAll(custodyLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                        CustodyTargetType.ANALYSIS_RESULT,
                        result.getAnalysisResultId()
                ));
            }
        }

        List<Report> reports = reportRepository.findByEvidenceIdOrderByCreatedAtDesc(evidenceId);
        for (Report report : reports) {
            if (report.getReportId() == null) {
                continue;
            }
            logs.addAll(custodyLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                    CustodyTargetType.REPORT,
                    report.getReportId()
            ));
        }

        return logs;
    }

    private List<CustodyLog> sort(List<CustodyLog> logs, boolean ascending) {
        Comparator<CustodyLog> comparator = Comparator
                .comparing(CustodyLog::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CustodyLog::getLogId, Comparator.nullsLast(Comparator.naturalOrder()));
        if (!ascending) {
            comparator = comparator.reversed();
        }
        return logs.stream().sorted(comparator).toList();
    }
}
