package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "blockchain.anchor")
public class BlockchainAnchorProperties {

    private boolean enabled = true;

    /** simulated | http */
    private String mode = "simulated";

    private String network = "local-simulated";

    private String httpUrl = "";

    private String dailyCron = "0 0 1 * * *";

    private boolean schedulerEnabled = true;

    public boolean isHttpMode() {
        return "http".equalsIgnoreCase(mode);
    }
}
