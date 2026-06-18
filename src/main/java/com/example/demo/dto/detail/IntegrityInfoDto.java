package com.example.demo.dto.detail;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IntegrityInfoDto {

    private String hashAlgorithm;
    private String originalHash;
    private String copyHash;
    private String copyStatus;
    private boolean chainValid;
    @JsonProperty("isChainValid")
    private boolean chainValidAlias;
    private String verificationStatus;
    private Integer recoveryScore;
    private Integer dataLossPercent;
    private String recoveryGrade;
}
