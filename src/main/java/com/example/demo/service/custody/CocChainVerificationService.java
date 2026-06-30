package com.example.demo.service.custody;

import com.example.demo.service.evidence.EvidenceAccessService;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.dto.CocChainVerifyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RQ-HIS-107: 증거별 CoC 해시 체인 검증 API.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CocChainVerificationService {

    private final EvidenceAccessService evidenceAccessService;
    private final CustodyLogService custodyLogService;

    public CocChainVerifyResponse verifyEvidenceChain(User user, Long evidenceId) {
        Evidence evidence = evidenceAccessService.requireOwned(user, evidenceId);

        return toResponse(evidence.getEvidenceId(), custodyLogService.verifyTargetChain(
                CustodyTargetType.EVIDENCE,
                evidence.getEvidenceId()
        ));
    }

    private CocChainVerifyResponse toResponse(
            Long evidenceId,
            CustodyLogService.TargetChainVerifyResult result
    ) {
        if (result.valid()) {
            String message = result.logCount() == 0
                    ? "검증할 감사 로그가 없습니다."
                    : "CoC 해시 체인 검증에 성공했습니다.";
            return CocChainVerifyResponse.builder()
                    .evidenceId(evidenceId)
                    .valid(true)
                    .logCount(result.logCount())
                    .message(message)
                    .build();
        }

        String message = switch (result.failureReason()) {
            case "PREVIOUS_HASH_MISMATCH" -> "로그 체인 연결이 끊어졌습니다.";
            case "HASH_MISMATCH" -> "로그 해시 재계산 결과가 일치하지 않습니다.";
            default -> "CoC 해시 체인 검증에 실패했습니다.";
        };

        return CocChainVerifyResponse.builder()
                .evidenceId(evidenceId)
                .valid(false)
                .logCount(result.logCount())
                .brokenAtLogId(result.brokenAtLogId())
                .failureReason(result.failureReason())
                .message(message)
                .build();
    }
}
