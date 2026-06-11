package com.example.demo.dto.detail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IntegrityInfoDto {

    private String hashAlgorithm;
    private String originalHash;
    private boolean chainValid;
    private String verificationStatus;
}
