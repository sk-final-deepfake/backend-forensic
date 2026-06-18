package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BlockchainInfoDto {

    private String status;
    private String anchorType;
    private String subjectHash;
    private String transactionHash;
    private String anchoredAt;
    private String network;
}
