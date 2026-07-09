package com.example.demo.util;

public final class CaseNumberSupport {

    private CaseNumberSupport() {
    }

    public static String resolve(String caseNumber, String caseName) {
        if (caseNumber != null && !caseNumber.isBlank()) {
            return caseNumber.trim();
        }
        if (caseName == null || caseName.isBlank()) {
            return "";
        }
        return caseName.trim();
    }
}
