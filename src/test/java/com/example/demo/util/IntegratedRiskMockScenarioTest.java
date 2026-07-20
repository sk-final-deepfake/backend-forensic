package com.example.demo.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.dto.AnalysisResponseMessage;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IntegratedRiskMockScenarioTest {

    record Scenario(
            String id,
            Double deepfakeScore,
            List<Double> forgeryScores,
            boolean deepfakeAvailable,
            String errorCode,
            double expectedScore,
            String expectedMethod,
            RiskLevel expectedLevel
    ) {
        @Override
        public String toString() {
            return id;
        }
    }

    static List<Scenario> mockScenarios() {
        double w12_88 = IntegratedRiskCalculator.dynamicWeightedMean01(0.12, 0.88) * 100.0;
        double w22_71 = IntegratedRiskCalculator.dynamicWeightedMean01(0.22, 0.71) * 100.0;

        return List.of(
                new Scenario(
                        "01_ai_response_both_lanes",
                        0.12,
                        List.of(0.88),
                        true,
                        null,
                        Math.round(w12_88 * 100.0) / 100.0,
                        "dynamic_weighted_deepfake_forgery",
                        RiskLevel.HIGH
                ),
                new Scenario(
                        "02_ai_response_forgery_skip",
                        0.72,
                        List.of(),
                        true,
                        null,
                        72.0,
                        "deepfake_only",
                        RiskLevel.HIGH
                ),
                new Scenario(
                        "03_ai_response_face_gate",
                        0.99,
                        List.of(0.55),
                        false,
                        "NO_HUMAN_FACE",
                        55.0,
                        "forgery_only",
                        RiskLevel.MEDIUM
                ),
                new Scenario(
                        "04_module_results_spatial_temporal",
                        null,
                        null,
                        true,
                        null,
                        Math.round(w22_71 * 100.0) / 100.0,
                        "dynamic_weighted_deepfake_forgery",
                        RiskLevel.MEDIUM
                ),
                new Scenario(
                        "05_both_unavailable",
                        null,
                        List.of(),
                        true,
                        null,
                        0.0,
                        "none",
                        RiskLevel.LOW
                )
        );
    }

    @ParameterizedTest
    @MethodSource("mockScenarios")
    void mockScenario_outputsExpectedRisk(Scenario scenario) {
        IntegratedRiskCalculator.IntegratedRisk result;
        if ("04_module_results_spatial_temporal".equals(scenario.id)) {
            result = IntegratedRiskCalculator.fromModuleResults(
                    List.of(
                            module("deepfake", 0.22),
                            module("forgery_spatial", 0.30),
                            module("forgery_temporal", 0.71)
                    ),
                    null,
                    null
            );
        } else {
            AnalysisResponseMessage response = AnalysisResponseMessage.builder()
                    .errorCode(scenario.errorCode)
                    .results(List.of(
                            AnalysisResponseMessage.AnalysisVideoResultItem.builder()
                                    .deepfakeScore(scenario.deepfakeScore)
                                    .modelScores(buildModelScores(scenario.forgeryScores))
                                    .build()
                    ))
                    .build();
            result = IntegratedRiskCalculator.fromAiResponse(response);
        }

        assertThat(result.riskScore()).isEqualTo(scenario.expectedScore());
        assertThat(result.method()).isEqualTo(scenario.expectedMethod());
        assertThat(result.riskLevel()).isEqualTo(scenario.expectedLevel());
    }

    private static List<AnalysisResponseMessage.ModelScoreItem> buildModelScores(List<Double> forgeryScores) {
        if (forgeryScores == null || forgeryScores.isEmpty()) {
            return List.of();
        }
        return List.of(
                AnalysisResponseMessage.ModelScoreItem.builder()
                        .moduleName("forgery_spatial")
                        .score(forgeryScores.get(0))
                        .build()
        );
    }

    private static AnalysisModuleResult module(String name, double score) {
        AnalysisModuleResult module = new AnalysisModuleResult();
        module.setModuleName(name);
        module.setScore(score);
        return module;
    }
}
