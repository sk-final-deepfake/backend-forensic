package com.example.demo.service.evidence;

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

    public static String manifestKey(String caseKey, Long evidenceId) {
        return "cases/" + caseKey + "/" + evidenceId + "/manifest/evidence-manifest.json";
    }

    /** HLS 재생 전용 prefix (끝에 / 포함) */
    public static String hlsPrefix(Long evidenceId) {
        return "hls/" + evidenceId + "/";
    }

    public static String hlsMasterKey(Long evidenceId) {
        return hlsPrefix(evidenceId) + "master.m3u8";
    }

    private static String sanitize(String value) {
        return value.trim()
                .replaceAll("[\\\\/]+", "-")
                .replaceAll("\\s+", "-")
                .toLowerCase(Locale.ROOT);
    }
}
