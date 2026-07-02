package com.example.demo.service.report;

import com.example.demo.domain.Report;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.ReportRepository;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.custody.ReportCustodyLogService;
import com.example.demo.service.evidence.HashService;
import com.example.demo.util.PdfDocumentWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportPdfStorageService {

    private final ReportRepository reportRepository;
    private final HashService hashService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final ReportCustodyLogService reportCustodyLogService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    public Report persistAnalysisReport(
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

    public Report persistCompareReport(
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

    private byte[] buildPdfWithQr(String title, List<String> lines) {
        byte[] draft = PdfDocumentWriter.writeReport(title, lines, null);
        String qrHash = hashService.generateSha256(draft);
        return PdfDocumentWriter.writeReport(title, lines, qrHash);
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
}
