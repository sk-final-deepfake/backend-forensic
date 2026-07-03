package com.example.demo.service.blockchain.client;

import com.example.demo.domain.enums.BlockchainAnchorType;

/**
 * INF Fabric Anchor Gateway payload (hash anchoring only).
 */
public record BlockchainAnchorRequest(
        String subjectHash,
        BlockchainAnchorType anchorType,
        String network,
        String clientId,
        Long evidenceId,
        Long reportId,
        String merkleBatchDate,
        Integer merkleLeafCount
) {
}
