package com.example.demo.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.domain.enums.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntegratedRiskCalculatorTest {

    @Test
    void dynamicWeightedMean_matchesAiFormula() {
        double result = IntegratedRiskCalculator.dynamicWeightedMean01(0.12, 0.88);
        assertThat(result).isEqualTo((0.12 * 0.12 + 0.88 * 0.88) / (0.12 + 0.88));
    }

    @Test
    void integrate_bothLanes_usesDynamicWeightedMean() {
        IntegratedRiskCalculator.IntegratedRisk risk = IntegratedRiskCalculator.integrate(
                0.12,
                List.of(0.88),
                true
        );

        double expected = IntegratedRiskCalculator.dynamicWeightedMean01(0.12, 0.88) * 100.0;
        assertThat(risk.riskScore()).isEqualTo(Math.round(expected * 100.0) / 100.0);
        assertThat(risk.method()).isEqualTo("dynamic_weighted_deepfake_forgery");
    }

    @Test
    void integrate_forgerySkip_usesDeepfakeOnly() {
        IntegratedRiskCalculator.IntegratedRisk risk = IntegratedRiskCalculator.integrate(
                0.72,
                List.of(),
                true
        );

        assertThat(risk.riskScore()).isEqualTo(72.0);
        assertThat(risk.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(risk.method()).isEqualTo("deepfake_only");
    }

    @Test
    void integrate_softFaceGate_usesForgeryOnly() {
        IntegratedRiskCalculator.IntegratedRisk risk = IntegratedRiskCalculator.integrate(
                0.99,
                List.of(0.55),
                false
        );

        assertThat(risk.riskScore()).isEqualTo(55.0);
        assertThat(risk.method()).isEqualTo("forgery_only");
    }

    @Test
    void integrate_forgeryMaxAcrossSpatialAndTemporal() {
        IntegratedRiskCalculator.IntegratedRisk risk = IntegratedRiskCalculator.integrate(
                0.20,
                List.of(0.10, 0.65),
                true
        );

        double expected = IntegratedRiskCalculator.dynamicWeightedMean01(0.20, 0.65) * 100.0;
        assertThat(risk.riskScore()).isEqualTo(Math.round(expected * 100.0) / 100.0);
    }
}
