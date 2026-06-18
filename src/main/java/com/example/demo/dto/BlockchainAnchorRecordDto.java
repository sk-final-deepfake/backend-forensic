package com.example.demo.dto;

import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BlockchainAnchorRecordDto {

    private Long anchorId;
    private BlockchainAnchorType anchorType;
    private BlockchainAnchorStatus status;
    private String subjectHash;
    private String transactionHash;
    private Long blockNumber;
    private String network;
    private String anchoredAt;
    private Long evidenceId;
    private Long reportId;
    private String merkleBatchDate;
    private Integer merkleLeafCount;
    private String message;
}
