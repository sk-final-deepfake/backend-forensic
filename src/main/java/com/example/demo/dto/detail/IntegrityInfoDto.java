package com.example.demo.dto.detail;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
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
    /** SK-1017: CoC 로그 건수 */
    private int cocLogCount;
    /** SK-1017: CoC 해시 체인 검증 통과 여부 */
    private boolean cocChainVerified;
    private String cocVerificationMessage;

    /** RQ-SEC-153: 서명·CoC·블록체인 종합 통과 여부 */
    private boolean integrityValid;
    /** RQ-SEC-153: 보안 경고 상태 (OK | SECURITY_ALERT) */
    private String securityStatus;
    /** 실패한 무결성 검사 항목 */
    private List<SecurityCheckDto> failedChecks;
}
