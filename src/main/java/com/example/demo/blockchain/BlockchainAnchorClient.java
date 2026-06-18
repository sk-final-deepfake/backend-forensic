package com.example.demo.blockchain;

import com.example.demo.domain.enums.BlockchainAnchorType;

public interface BlockchainAnchorClient {

    BlockchainAnchorResult anchor(String subjectHash, BlockchainAnchorType anchorType);
}
