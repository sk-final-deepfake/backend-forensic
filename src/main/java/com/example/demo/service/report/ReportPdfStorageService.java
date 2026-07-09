package com.example.demo.service.report;

import com.example.demo.domain.Report;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.ReportRepository;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.custody.ReportCustodyLogService;
import com.example.demo.service.evidence.HashService;
import com.example.demo.util.PdfDocumentWriter;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportPdfStorageService {

    private final ReportRepository reportRepository;
    private final HashService hashService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final ReportCustodyLogService reportCustodyLogService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${report.public-verify-base-url:http://localhost:3000/verify}")
    private String publicVerifyBaseUrl;

    public Report persistAnalysisReport(
            Long analysisResultId,
            Long evidenceId,
            Long userId,
            String fileName,
            List<String> lines,
            String title
    ) {
        ReportVerificationPayload verificationPayload = createVerificationPayload();
        byte[] pdfBytes = buildPdfWithQr(
                title,
                lines,
                verificationPayload.reportNo(),
                verificationPayload.verifyUrl(),
                verificationPayload.verificationCode()
        );
        Path reportPath = storePdf("evidence", evidenceId, fileName, pdfBytes);
        Report report = new Report();
        report.setAnalysisResultId(analysisResultId);
        report.setEvidenceId(evidenceId);
        report.setCreatedBy(userId);
        report.setReportFileName(fileName);
        report.setReportNo(verificationPayload.reportNo());
        report.setVerificationToken(verificationPayload.verificationToken());
        report.setVerificationCode(verificationPayload.verificationCode());
        report.setStoragePath(reportPath.toString());
        report.setReportHash(hashService.generateSha256(pdfBytes));
        report.setFileSize((long) pdfBytes.length);
        report.setCreatedAt(LocalDateTime.now());
        Report saved = reportRepository.save(report);
        recordReportSideEffects(saved, userId);
        return saved;
    }

    public Report persistCompareReport(
            Long compareId,
            Long evidenceId,
            Long userId,
            String fileName,
            List<String> lines,
            String title
    ) {
        ReportVerificationPayload verificationPayload = createVerificationPayload();
        byte[] pdfBytes = buildPdfWithQr(
                title,
                lines,
                verificationPayload.reportNo(),
                verificationPayload.verifyUrl(),
                verificationPayload.verificationCode()
        );
        Path reportPath = storePdf("compare", compareId, fileName, pdfBytes);
        Report report = new Report();
        report.setCompareId(compareId);
        report.setEvidenceId(evidenceId);
        report.setCreatedBy(userId);
        report.setReportFileName(fileName);
        report.setReportNo(verificationPayload.reportNo());
        report.setVerificationToken(verificationPayload.verificationToken());
        report.setVerificationCode(verificationPayload.verificationCode());
        report.setStoragePath(reportPath.toString());
        report.setReportHash(hashService.generateSha256(pdfBytes));
        report.setFileSize((long) pdfBytes.length);
        report.setCreatedAt(LocalDateTime.now());
        Report saved = reportRepository.save(report);
        recordReportSideEffects(saved, userId);
        return saved;
    }

    public byte[] readStoredPdf(String storagePath) {
        try {
            return Files.readAllBytes(Paths.get(storagePath));
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_READ_FAILED", "PDF 리포트를 읽을 수 없습니다.");
        }
    }

    public boolean verifyStoredFileHash(Report report) {
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

    private byte[] buildPdfWithQr(
            String title,
            List<String> lines,
            String reportNo,
            String qrContent,
            String verificationCode
    ) {
        return PdfDocumentWriter.writeReport(title, lines, qrContent, verificationCode, reportNo);
    }

    private void recordReportSideEffects(Report report, Long userId) {
        try {
            blockchainAnchorService.anchorReportHash(report, userId);
        } catch (Exception ex) {
            log.warn("Report hash blockchain anchor failed reportId={} evidenceId={}: {}",
                    report.getReportId(), report.getEvidenceId(), ex.getMessage());
        }

        try {
            reportCustodyLogService.recordReportCreated(userId, report);
        } catch (Exception ex) {
            log.warn("Report custody log failed reportId={} evidenceId={}: {}",
                    report.getReportId(), report.getEvidenceId(), ex.getMessage());
        }
    }

    private ReportVerificationPayload createVerificationPayload() {
        String token = "vrf_" + UUID.randomUUID().toString().replace("-", "");
        String suffix = token.substring(token.length() - 8).toUpperCase(Locale.ROOT);
        String reportNo = "RPT-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + suffix;
        String codeSeed = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        String verificationCode = "VF-" + codeSeed.substring(0, 4) + "-" + codeSeed.substring(4);
        return new ReportVerificationPayload(reportNo, token, verificationCode, buildVerifyUrl(token));
    }

    private String buildVerifyUrl(String token) {
        String separator = publicVerifyBaseUrl.contains("?") ? "&" : "?";
        return publicVerifyBaseUrl + separator + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
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

    private record ReportVerificationPayload(
            String reportNo,
            String verificationToken,
            String verificationCode,
            String verifyUrl
    ) {
    }
}
