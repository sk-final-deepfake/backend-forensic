package com.example.demo.util;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class CaseKeyNormalizer {

    private CaseKeyNormalizer() {
    }

    /**
     * FE 라우트·쿼리에서 이중 인코딩된 사건명을 안전하게 복원합니다.
     */
    public static String normalize(String caseKey) {
        if (caseKey == null) {
            return null;
        }

        String current = caseKey.trim();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String decoded = URLDecoder.decode(current.replace("+", " "), StandardCharsets.UTF_8);
                if (decoded.equals(current)) {
                    break;
                }
                current = decoded.trim();
            } catch (IllegalArgumentException ex) {
                break;
            }
        }
        return current;
    }
}
