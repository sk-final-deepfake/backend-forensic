package com.example.demo.blockchain;

import com.example.demo.config.BlockchainAnchorProperties;
import com.example.demo.domain.enums.BlockchainAnchorType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "blockchain.anchor.mode", havingValue = "http")
public class HttpBlockchainAnchorClient implements BlockchainAnchorClient {

    private final BlockchainAnchorProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public BlockchainAnchorResult anchor(String subjectHash, BlockchainAnchorType anchorType) {
        if (properties.getHttpUrl() == null || properties.getHttpUrl().isBlank()) {
            return new BlockchainAnchorResult(null, null, false, "blockchain.anchor.http-url is not configured");
        }

        try {
            Map<?, ?> response = restClient.post()
                    .uri(properties.getHttpUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "subjectHash", subjectHash,
                            "anchorType", anchorType.name()
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("transactionHash") == null) {
                return new BlockchainAnchorResult(null, null, false, "Blockchain gateway returned empty transactionHash");
            }

            Long blockNumber = response.get("blockNumber") instanceof Number number
                    ? number.longValue()
                    : null;
            return new BlockchainAnchorResult(
                    String.valueOf(response.get("transactionHash")),
                    blockNumber,
                    true,
                    null
            );
        } catch (Exception ex) {
            return new BlockchainAnchorResult(null, null, false, ex.getMessage());
        }
    }
}
