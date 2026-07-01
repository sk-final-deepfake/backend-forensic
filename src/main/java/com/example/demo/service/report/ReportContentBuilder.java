package com.example.demo.service.report;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CompareVerification;
import com.example.demo.domain.Evidence;
import com.example.demo.dto.compare.CompareFileInfoDto;
import com.example.demo.dto.compare.CompareItemDto;
import com.example.demo.util.ApiDateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReportContentBuilder {

    public List<String> buildEvidenceLines(
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

    public List<String> buildCompareLines(
            CompareVerification verification,
            CompareFileInfoDto originalInfo,
            CompareFileInfoDto candidateInfo,
            List<CompareItemDto> items
    ) {
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
        return lines;
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
}
