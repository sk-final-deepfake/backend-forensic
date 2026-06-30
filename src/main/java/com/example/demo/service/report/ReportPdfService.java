package com.example.demo.service.report;

import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.compare.CompareVerificationService;
import com.example.demo.service.custody.ReportCustodyLogService;
import com.example.demo.service.evidence.EvidenceAccessService;
import com.example.demo.service.evidence.HashService;
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
import com.example.demo.util.PdfDocumentWriter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportPdfService {

    private final EvidenceAccessService evidenceAccessService;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisModuleResultRepository analysisModuleResultRepository;
    private final ReportRepository reportRepository;
    private final CompareVerificationService compareVerificationService;
    private final HashService hashService;
    private final ObjectMapper objectMapper;
    private final BlockchainAnchorService blockchainAnchorService;
    private final ReportCustodyLogService reportCustodyLogService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public ReportPdfPayload generateEvidenceReport(User user, Long evidenceId) {
        Evidence evidence = evidenceAccessService.requireOwned(user, evidenceId);
        AnalysisRequest request = requireCompletedAnalysis(evidenceId);
        AnalysisResult result = requireAnalysisResult(request.getAnalysisRequestId());
        List<AnalysisModuleResult> modules = analysisModuleResultRepository
                .findByAnalysisResultIdOrderByCreatedAtAsc(result.getAnalysisResultId());

        List<String> lines = buildEvidenceLines(evidence, request, result, modules);
        Report report = persistAnalysisReport(
                result.getAnalysisResultId(),
                evidenceId,
                user.getUserId(),
                "analysis-report-" + evidenceId + ".pdf",
                lines,
                "ForenShield Analysis Report"
        );

        byte[] pdfBytes = readStoredPdf(report.getStoragePath());
        reportCustodyLogService.recordReportDownloaded(user.getUserId(), report);
        return new ReportPdfPayload(report.getReportFileName(), pdfBytes, report.getReportHash());
    }

    @Transactional
    public ReportPdfPayload generateCompareReport(User user, Long compareId) {
        CompareVerification verification = compareVerificationService.requireOwnedVerification(user, compareId);
        List<CompareItemDto> items = deserializeItems(verification.getResultJson());
        Evidence original = evidenceAccessService.requireOwned(user, verification.getOriginalEvidenceId());
        CompareFileInfoDto originalInfo = compareVerificationService.getOriginalFileInfo(
                user,
                verification.getOriginalEvidenceId()
        );
        CompareFileInfoDto candidateInfo = compareVerificationService.getCandidateFileInfo(user, compareId);

        List<String> lines = new ArrayList<>();
        lines.add("Compare ID: " + verification.getCompareId());
        lines.add("Verdict: " + verification.getVerdict());
        lines.add("Match Count: " + verification.getMatchCount());
        lines.add("Mismatch Count: " + verification.getMismatchCount());
        lines.add("Skipped Count: " + verification.getSkippedCount());
        lines.add("Created At: " + ApiDateTimeFormatter.formatUtc(verification.getCreatedAt()));
        lines.add(" ");
        lines.add("=== Original File Information ===");
        appendCompareFileInfoLines(lines, originalInfo);
        lines.add(" ");
        lines.add("=== Candidate File Information ===");
        appendCompareFileInfoLines(lines, candidateInfo);
        lines.add(" ");
        lines.add("=== Comparison Results ===");
        for (CompareItemDto item : items) {
            lines.add(item.getLabel() + " | original=" + item.getOriginalValue()
                    + " | candidate=" + item.getCandidateValue()
                    + " | result=" + item.getResult());
        }

        Report report = persistCompareReport(
                compareId,
                original.getEvidenceId(),
                user.getUserId(),
                "compare-report-" + compareId + ".pdf",
                lines,
                "ForenShield Compare Verification Report"
        );
        byte[] pdfBytes = readStoredPdf(report.getStoragePath());
        reportCustodyLogService.recordReportDownloaded(user.getUserId(), report);
        return new ReportPdfPayload(report.getReportFileName(), pdfBytes, report.getReportHash());
    }

    private void appendCompareFileInfoLines(List<String> lines, CompareFileInfoDto info) {
        if (info.getEvidenceId() != null) {
            lines.add("Evidence ID: " + info.getEvidenceId());
        }
        if (info.getCompareId() != null) {
            lines.add("Compare ID: " + info.getCompareId());
        }
        lines.add("File Name: " + info.getFileName());
        lines.add("File Size: " + info.getFileSize());
        lines.add("SHA-256: " + info.getSha256());
        if (info.getCaseName() != null) {
            lines.add("Case Name: " + info.getCaseName());
        }
        if (info.getCaseNumber() != null) {
            lines.add("Case Number: " + info.getCaseNumber());
        }
        if (info.getFileType() != null) {
            lines.add("File Type: " + info.getFileType());
        }
        if (info.getMimeType() != null) {
            lines.add("MIME Type: " + info.getMimeType());
        }
        if (info.getUploadedAt() != null) {
            lines.add("Uploaded At: " + info.getUploadedAt());
        }
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

        boolean valid = verifyStoredFileHash(report);
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

    private List<String> buildEvidenceLines(
            Evidence evidence,
            AnalysisRequest request,
            AnalysisResult result,
            List<AnalysisModuleResult> modules
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("Evidence ID: " + evidence.getEvidenceId());
        lines.add("File Name: " + evidence.getFileName());
        lines.add("SHA-256: " + evidence.getOriginalHashValue());
        lines.add("Analysis Status: " + request.getStatus());
        lines.add("Risk Level: " + (result.getRiskLevel() == null ? "-" : result.getRiskLevel()));
        lines.add("Risk Score: " + (result.getRiskScore() == null ? "-" : result.getRiskScore()));
        lines.add("Confidence: " + (result.getConfidenceScore() == null ? "-" : result.getConfidenceScore()));
        lines.add("Summary: " + (result.getSummary() == null ? "-" : result.getSummary()));
        lines.add("Analyzed At: " + ApiDateTimeFormatter.formatUtc(result.getAnalyzedAt()));

        for (AnalysisModuleResult module : modules) {
            lines.add("--- Module: " + module.getModuleName() + " ---");
            lines.add("Model: " + module.getModelName() + " v" + module.getModelVersion());
            lines.add("Detected: " + module.getDetected());
            lines.add("Score: " + module.getScore());
            lines.add("Confidence: " + module.getConfidence());
        }
        return lines;
    }

    private Report persistAnalysisReport(
            Long analysisResultId,
            Long evidenceId,
            Long userId,
            String fileName,
            List<String> lines,
            String title
    ) {
        byte[] pdfBytes = buildPdfWithQr(title, lines);
        Path reportPath = storePdf("evidence", evidenceId, fileName, pdfBytes);
        Report report = new Report();
        report.setAnalysisResultId(analysisResultId);
        report.setEvidenceId(evidenceId);
        report.setCreatedBy(userId);
        report.setReportFileName(fileName);
        report.setStoragePath(reportPath.toString());
        report.setReportHash(hashService.generateSha256(pdfBytes));
        report.setFileSize((long) pdfBytes.length);
        report.setCreatedAt(LocalDateTime.now());
        Report saved = reportRepository.save(report);
        blockchainAnchorService.anchorReportHash(saved, userId);
        reportCustodyLogService.recordReportCreated(userId, saved);
        return saved;
    }

    private Report persistCompareReport(
            Long compareId,
            Long evidenceId,
            Long userId,
            String fileName,
            List<String> lines,
            String title
    ) {
        byte[] pdfBytes = buildPdfWithQr(title, lines);
        Path reportPath = storePdf("compare", compareId, fileName, pdfBytes);
        Report report = new Report();
        report.setCompareId(compareId);
        report.setEvidenceId(evidenceId);
        report.setCreatedBy(userId);
        report.setReportFileName(fileName);
        report.setStoragePath(reportPath.toString());
        report.setReportHash(hashService.generateSha256(pdfBytes));
        report.setFileSize((long) pdfBytes.length);
        report.setCreatedAt(LocalDateTime.now());
        Report saved = reportRepository.save(report);
        blockchainAnchorService.anchorReportHash(saved, userId);
        reportCustodyLogService.recordReportCreated(userId, saved);
        return saved;
    }

    private byte[] buildPdfWithQr(String title, List<String> lines) {
        byte[] draft = PdfDocumentWriter.writeReport(title, lines, null);
        String qrHash = hashService.generateSha256(draft);
        return PdfDocumentWriter.writeReport(title, lines, qrHash);
    }

    private boolean verifyStoredFileHash(Report report) {
        if (report.getReportHash() == null || report.getStoragePath() == null) {
            return false;
        }
        try {
            byte[] stored = Files.readAllBytes(Paths.get(report.getStoragePath()));
            return report.getReportHash().equalsIgnoreCase(hashService.generateSha256(stored));
        } catch (IOException ex) {
            return false;
        }
    }

    private byte[] readStoredPdf(String storagePath) {
        try {
            return Files.readAllBytes(Paths.get(storagePath));
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_READ_FAILED", "PDF 리포트를 읽을 수 없습니다.");
        }
    }

    private Path storePdf(String category, Long id, String fileName, byte[] pdfBytes) {
        try {
            Path reportDir = Paths.get(uploadDir, "reports", category, String.valueOf(id));
            Files.createDirectories(reportDir);
            Path reportPath = reportDir.resolve(fileName);
            Files.write(reportPath, pdfBytes);
            return reportPath;
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_SAVE_FAILED", "PDF 리포트 저장에 실패했습니다.");
        }
    }

    private List<CompareItemDto> deserializeItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("비교 결과 역직렬화에 실패했습니다.", ex);
        }
    }

    public record ReportPdfPayload(String fileName, byte[] content, String reportHash) {
    }
}
