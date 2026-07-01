package com.example.demo.service.report;

import com.example.demo.service.compare.CompareVerificationAssembler;
import com.example.demo.service.compare.CompareVerificationService;
import com.example.demo.service.custody.ReportCustodyLogService;
import com.example.demo.service.evidence.EvidenceAccessService;
import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CompareVerification;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.dto.ReportVerifyResponse;
import com.example.demo.dto.compare.CompareFileInfoDto;
import com.example.demo.dto.compare.CompareItemDto;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.ReportRepository;
import com.example.demo.util.ApiDateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportPdfService {

    private final EvidenceAccessService evidenceAccessService;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisModuleResultRepository analysisModuleResultRepository;
    private final ReportRepository reportRepository;
    private final CompareVerificationService compareVerificationService;
    private final CompareVerificationAssembler compareVerificationAssembler;
    private final ReportContentBuilder reportContentBuilder;
    private final ReportPdfStorageService reportPdfStorageService;
    private final ReportCustodyLogService reportCustodyLogService;

    @Transactional
    public ReportPdfPayload generateEvidenceReport(User user, Long evidenceId) {
        Evidence evidence = evidenceAccessService.requireOwned(user, evidenceId);
        AnalysisRequest request = requireCompletedAnalysis(evidenceId);
        AnalysisResult result = requireAnalysisResult(request.getAnalysisRequestId());
        List<AnalysisModuleResult> modules = analysisModuleResultRepository
                .findByAnalysisResultIdOrderByCreatedAtAsc(result.getAnalysisResultId());

        List<String> lines = reportContentBuilder.buildEvidenceLines(evidence, request, result, modules);
        Report report = reportPdfStorageService.persistAnalysisReport(
                result.getAnalysisResultId(),
                evidenceId,
                user.getUserId(),
                "analysis-report-" + evidenceId + ".pdf",
                lines,
                "ForenShield Analysis Report"
        );

        byte[] pdfBytes = reportPdfStorageService.readStoredPdf(report.getStoragePath());
        reportCustodyLogService.recordReportDownloaded(user.getUserId(), report);
        return new ReportPdfPayload(report.getReportFileName(), pdfBytes, report.getReportHash());
    }

    @Transactional
    public ReportPdfPayload generateCompareReport(User user, Long compareId) {
        CompareVerification verification = compareVerificationService.requireOwnedVerification(user, compareId);
        List<CompareItemDto> items = compareVerificationAssembler.deserializeItems(verification.getResultJson());
        Evidence original = evidenceAccessService.requireOwned(user, verification.getOriginalEvidenceId());
        CompareFileInfoDto originalInfo = compareVerificationService.getOriginalFileInfo(
                user,
                verification.getOriginalEvidenceId()
        );
        CompareFileInfoDto candidateInfo = compareVerificationService.getCandidateFileInfo(user, compareId);

        List<String> lines = reportContentBuilder.buildCompareLines(
                verification, originalInfo, candidateInfo, items);

        Report report = reportPdfStorageService.persistCompareReport(
                compareId,
                original.getEvidenceId(),
                user.getUserId(),
                "compare-report-" + compareId + ".pdf",
                lines,
                "ForenShield Compare Verification Report"
        );
        byte[] pdfBytes = reportPdfStorageService.readStoredPdf(report.getStoragePath());
        reportCustodyLogService.recordReportDownloaded(user.getUserId(), report);
        return new ReportPdfPayload(report.getReportFileName(), pdfBytes, report.getReportHash());
    }

    @Transactional(readOnly = true)
    public ReportVerifyResponse verifyReportHash(User user, Long evidenceId, String reportHash) {
        evidenceAccessService.requireOwned(user, evidenceId);
        if (reportHash == null || reportHash.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "reportHash는 필수입니다.");
        }

        Report report = reportRepository.findByEvidenceIdAndReportHash(evidenceId, reportHash.trim())
                .orElseGet(() -> reportRepository.findTopByEvidenceIdOrderByCreatedAtDesc(evidenceId)
                        .filter(candidate -> reportHash.trim().equalsIgnoreCase(candidate.getReportHash()))
                        .orElse(null));

        if (report == null) {
            return ReportVerifyResponse.builder()
                    .valid(false)
                    .evidenceId(evidenceId)
                    .reportHash(reportHash.trim())
                    .message("일치하는 리포트를 찾을 수 없습니다.")
                    .build();
        }

        boolean valid = reportPdfStorageService.verifyStoredFileHash(report);
        return ReportVerifyResponse.builder()
                .valid(valid)
                .reportId(report.getReportId())
                .evidenceId(evidenceId)
                .reportHash(report.getReportHash())
                .reportFileName(report.getReportFileName())
                .createdAt(ApiDateTimeFormatter.formatUtc(report.getCreatedAt()))
                .message(valid ? "reportHash가 저장된 PDF와 일치합니다." : "reportHash가 저장된 PDF와 일치하지 않습니다.")
                .build();
    }

    private AnalysisRequest requireCompletedAnalysis(Long evidenceId) {
        AnalysisRequest request = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.CONFLICT, "ANALYSIS_NOT_FOUND", "분석 요청이 없습니다."));

        if (request.getStatus() != AnalysisStatus.COMPLETED) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "ANALYSIS_NOT_COMPLETED", "분석이 완료된 후 PDF 리포트를 생성할 수 있습니다.");
        }
        return request;
    }

    private AnalysisResult requireAnalysisResult(Long analysisRequestId) {
        return analysisResultRepository.findByAnalysisRequestId(analysisRequestId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.CONFLICT, "ANALYSIS_RESULT_NOT_FOUND", "분석 결과가 없습니다."));
    }

    public record ReportPdfPayload(String fileName, byte[] content, String reportHash) {
    }
}
