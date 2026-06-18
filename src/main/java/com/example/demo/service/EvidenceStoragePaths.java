package com.example.demo.service;

import com.example.demo.domain.Evidence;

import java.util.Locale;

public final class EvidenceStoragePaths {

    private EvidenceStoragePaths() {
    }

    public static String resolveCaseKey(Evidence evidence) {
        if (evidence.getCaseNumber() != null && !evidence.getCaseNumber().isBlank()) {
            return sanitize(evidence.getCaseNumber());
        }
        if (evidence.getCaseName() != null && !evidence.getCaseName().isBlank()) {
            return sanitize(evidence.getCaseName());
        }
        return "evidence-" + evidence.getEvidenceId();
    }

    public static String originalKey(String caseKey, Long evidenceId, String fileName) {
        return "cases/" + caseKey + "/" + evidenceId + "/original/" + fileName;
    }

    public static String copyKey(String caseKey, Long evidenceId, String fileName) {
        return "cases/" + caseKey + "/" + evidenceId + "/copy/" + fileName;
    }

    private static String sanitize(String value) {
        return value.trim()
                .replaceAll("[\\\\/]+", "-")
                .replaceAll("\\s+", "-")
                .toLowerCase(Locale.ROOT);
    }
}
