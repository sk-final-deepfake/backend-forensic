package com.example.demo.dto.detail;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IntegrityInfoDto {

    private String hashAlgorithm;
    private String originalHash;
    private boolean chainValid;
    @JsonProperty("isChainValid")
    private boolean chainValidAlias;
    private String verificationStatus;
}
