package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.worker")
public class AnalysisWorkerProperties {

    /** local | simulated | ai */
    private String mode = "local";

    private long stepDelayMs = 600;

    public boolean isAiMode() {
        return "ai".equalsIgnoreCase(mode);
    }
}
