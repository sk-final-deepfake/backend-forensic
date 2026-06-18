package com.example.demo.blockchain;

import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.service.HashService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "blockchain.anchor.mode", havingValue = "simulated", matchIfMissing = true)
public class SimulatedBlockchainAnchorClient implements BlockchainAnchorClient {

    private final HashService hashService;

    @Override
    public BlockchainAnchorResult anchor(String subjectHash, BlockchainAnchorType anchorType) {
        String payload = "simulated-anchor|" + anchorType.name() + "|" + subjectHash;
        String transactionHash = "0x" + hashService.generateSha256(payload.getBytes());
        return new BlockchainAnchorResult(transactionHash, 1L, true, null);
    }
}
