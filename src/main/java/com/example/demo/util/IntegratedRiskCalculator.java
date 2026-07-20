package com.example.demo.util;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.dto.AnalysisResponseMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Top-level riskScore from deepfake Late Fusion (F) and forgery lane max (G).
 *
 * <p>When both are available: {@code risk = (F² + G²) / (F + G) × 100}.
 * Matches AI {@code app.services.integrated_risk.integrate_risk_score}.
 */
public final class IntegratedRiskCalculator {

    private static final Set<String> SOFT_FACE_GATE_CODES = Set.of(
            "NO_HUMAN_FACE",
            "FACE_TOO_SMALL",
            "NO_FACE",
            "FACE_GATE"
    );

    private static final double MEDIUM_MIN = 40.0;
    private static final double HIGH_MIN = 70.0;

    public record IntegratedRisk(double riskScore, RiskLevel riskLevel, String method) {
    }

    private IntegratedRiskCalculator() {
    }

    public static IntegratedRisk fromAiResponse(AnalysisResponseMessage response) {
        if (response == null) {
            return none();
        }
        AnalysisResponseMessage.AnalysisVideoResultItem video = firstVideoResult(response.getResults());
        Double deepfakeScore = video != null ? video.getDeepfakeScore() : null;
        List<Double> forgeryScores = resolveForgeryScores(resolveModelScores(response, video));
        boolean deepfakeAvailable = !isSoftFaceGate(response.getErrorCode());
        return integrate(deepfakeScore, forgeryScores, deepfakeAvailable);
    }

    public static IntegratedRisk fromModuleResults(
            List<AnalysisModuleResult> moduleResults,
            String errorCode,
            Double deepfakeScoreFallback
    ) {
        Double deepfakeScore = resolveDeepfakeScore(moduleResults);
        if (deepfakeScore == null) {
            deepfakeScore = deepfakeScoreFallback;
        }
        List<Double> forgeryScores = resolveForgeryScoresFromModules(moduleResults);
        boolean deepfakeAvailable = !isSoftFaceGate(errorCode);
        return integrate(deepfakeScore, forgeryScores, deepfakeAvailable);
    }

    public static IntegratedRisk integrate(
            Double deepfakeScore01,
            List<Double> forgeryScores01,
            boolean deepfakeAvailable
    ) {
        Double fusion = null;
        if (deepfakeAvailable && deepfakeScore01 != null) {
            fusion = clamp01(deepfakeScore01);
        }

        Double forgeryMax = maxForgery(forgeryScores01);

        double peak01;
        String method;
        if (fusion != null && forgeryMax != null) {
            peak01 = dynamicWeightedMean01(fusion, forgeryMax);
            method = "dynamic_weighted_deepfake_forgery";
        } else if (fusion != null) {
            peak01 = fusion;
            method = "deepfake_only";
        } else if (forgeryMax != null) {
            peak01 = forgeryMax;
            method = "forgery_only";
        } else {
            return none();
        }

        double riskScore = round2(peak01 * 100.0);
        return new IntegratedRisk(riskScore, riskLevelFromScore(riskScore), method);
    }

    public static double dynamicWeightedMean01(double fusion, double forgery) {
        double f = clamp01(fusion);
        double g = clamp01(forgery);
        double total = f + g;
        if (total <= 0.0) {
            return 0.0;
        }
        return (f * f + g * g) / total;
    }

    private static IntegratedRisk none() {
        return new IntegratedRisk(0.0, RiskLevel.LOW, "none");
    }

    private static boolean isSoftFaceGate(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return false;
        }
        return SOFT_FACE_GATE_CODES.contains(errorCode.trim().toUpperCase(Locale.ROOT));
    }

    private static AnalysisResponseMessage.AnalysisVideoResultItem firstVideoResult(
            List<AnalysisResponseMessage.AnalysisVideoResultItem> results
    ) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    private static List<AnalysisResponseMessage.ModelScoreItem> resolveModelScores(
            AnalysisResponseMessage response,
            AnalysisResponseMessage.AnalysisVideoResultItem video
    ) {
        if (video != null && video.getModelScores() != null && !video.getModelScores().isEmpty()) {
            return video.getModelScores();
        }
        if (response.getModelScores() != null && !response.getModelScores().isEmpty()) {
            return response.getModelScores();
        }
        return List.of();
    }

    private static List<Double> resolveForgeryScores(List<AnalysisResponseMessage.ModelScoreItem> modelScores) {
        List<Double> scores = new ArrayList<>();
        if (modelScores == null) {
            return scores;
        }
        for (AnalysisResponseMessage.ModelScoreItem item : modelScores) {
            if (item == null || item.getScore() == null) {
                continue;
            }
            String key = normalizeModuleKey(item.getModuleName());
            if ("forgery_spatial".equals(key) || "forgery_temporal".equals(key)) {
                scores.add(clamp01(item.getScore()));
            }
        }
        return scores;
    }

    private static List<Double> resolveForgeryScoresFromModules(List<AnalysisModuleResult> moduleResults) {
        List<Double> scores = new ArrayList<>();
        if (moduleResults == null) {
            return scores;
        }
        for (AnalysisModuleResult module : moduleResults) {
            if (module == null || module.getScore() == null) {
                continue;
            }
            String key = normalizeModuleKey(module.getModuleName());
            if ("forgery_spatial".equals(key) || "forgery_temporal".equals(key)) {
                scores.add(clamp01(module.getScore()));
            }
        }
        return scores;
    }

    private static Double resolveDeepfakeScore(List<AnalysisModuleResult> moduleResults) {
        if (moduleResults == null) {
            return null;
        }
        for (AnalysisModuleResult module : moduleResults) {
            if (module == null || module.getScore() == null) {
                continue;
            }
            String key = normalizeModuleKey(module.getModuleName());
            if (isFusionModuleKey(key)) {
                return clamp01(module.getScore());
            }
        }
        return null;
    }

    private static Double maxForgery(List<Double> forgeryScores01) {
        if (forgeryScores01 == null || forgeryScores01.isEmpty()) {
            return null;
        }
        double max = 0.0;
        boolean found = false;
        for (Double score : forgeryScores01) {
            if (score == null) {
                continue;
            }
            found = true;
            max = Math.max(max, clamp01(score));
        }
        return found ? max : null;
    }

    private static boolean isFusionModuleKey(String key) {
        return "deepfake".equals(key)
                || "late_fusion".equals(key)
                || "fusion".equals(key)
                || "latefusion".equals(key);
    }

    private static String normalizeModuleKey(String moduleName) {
        if (moduleName == null) {
            return "";
        }
        return moduleName.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static RiskLevel riskLevelFromScore(double riskScore) {
        if (riskScore >= HIGH_MIN) {
            return RiskLevel.HIGH;
        }
        if (riskScore >= MEDIUM_MIN) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }
}
