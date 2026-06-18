package com.example.demo.service;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CompareVerification;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.dto.compare.CompareItemDto;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.EvidenceRepository;
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

    private final EvidenceRepository evidenceRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisModuleResultRepository analysisModuleResultRepository;
    private final ReportRepository reportRepository;
    private final CompareVerificationService compareVerificationService;
    private final HashService hashService;
    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public ReportPdfPayload generateEvidenceReport(User user, Long evidenceId) {
        Evidence evidence = evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, user.getUserId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거를 찾을 수 없습니다."));

        AnalysisRequest request = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.CONFLICT, "ANALYSIS_NOT_FOUND", "분석 요청이 없습니다."));

        if (request.getStatus() != AnalysisStatus.COMPLETED) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "ANALYSIS_NOT_COMPLETED", "분석이 완료된 후 PDF 리포트를 생성할 수 있습니다.");
        }

        AnalysisResult result = analysisResultRepository.findByAnalysisRequestId(request.getAnalysisRequestId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.CONFLICT, "ANALYSIS_RESULT_NOT_FOUND", "분석 결과가 없습니다."));

        List<AnalysisModuleResult> modules = analysisModuleResultRepository
                .findByAnalysisResultIdOrderByCreatedAtAsc(result.getAnalysisResultId());

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

        byte[] pdfBytes = PdfDocumentWriter.writeReport("ForenShield Analysis Report", lines);
        Report report = persistReport(
                result.getAnalysisResultId(),
                evidenceId,
                user.getUserId(),
                "analysis-report-" + evidenceId + ".pdf",
                pdfBytes
        );

        return new ReportPdfPayload(report.getReportFileName(), pdfBytes);
    }

    @Transactional(readOnly = true)
    public ReportPdfPayload generateCompareReport(User user, Long compareId) {
        CompareVerification verification = compareVerificationService.requireOwnedVerification(user, compareId);
        List<CompareItemDto> items = deserializeItems(verification.getResultJson());

        List<String> lines = new ArrayList<>();
        lines.add("Compare ID: " + verification.getCompareId());
        lines.add("Original Evidence ID: " + verification.getOriginalEvidenceId());
        lines.add("Candidate File: " + verification.getCandidateFileName());
        lines.add("Verdict: " + verification.getVerdict());
        lines.add("Match Count: " + verification.getMatchCount());
        lines.add("Mismatch Count: " + verification.getMismatchCount());
        lines.add("Skipped Count: " + verification.getSkippedCount());
        lines.add("Created At: " + ApiDateTimeFormatter.formatUtc(verification.getCreatedAt()));
        lines.add(" ");
        for (CompareItemDto item : items) {
            lines.add(item.getLabel() + " | original=" + item.getOriginalValue()
                    + " | candidate=" + item.getCandidateValue()
                    + " | result=" + item.getResult());
        }

        byte[] pdfBytes = PdfDocumentWriter.writeReport("ForenShield Compare Verification Report", lines);
        return new ReportPdfPayload("compare-report-" + compareId + ".pdf", pdfBytes);
    }

    private Report persistReport(
            Long analysisResultId,
            Long evidenceId,
            Long userId,
            String fileName,
            byte[] pdfBytes
    ) {
        Path reportPath = storePdf(evidenceId, fileName, pdfBytes);
        Report report = new Report();
        report.setAnalysisResultId(analysisResultId);
        report.setEvidenceId(evidenceId);
        report.setCreatedBy(userId);
        report.setReportFileName(fileName);
        report.setStoragePath(reportPath.toString());
        report.setReportHash(hashService.generateSha256(pdfBytes));
        report.setFileSize((long) pdfBytes.length);
        report.setCreatedAt(LocalDateTime.now());
        return reportRepository.save(report);
    }

    private Path storePdf(Long evidenceId, String fileName, byte[] pdfBytes) {
        try {
            Path reportDir = Paths.get(uploadDir, "reports", String.valueOf(evidenceId));
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

    public record ReportPdfPayload(String fileName, byte[] content) {
    }
}
