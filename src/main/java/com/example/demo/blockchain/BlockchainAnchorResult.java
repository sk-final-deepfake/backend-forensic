package com.example.demo.blockchain;

public record BlockchainAnchorResult(
        String transactionHash,
        Long blockNumber,
        boolean success,
        String errorMessage
) {
}
