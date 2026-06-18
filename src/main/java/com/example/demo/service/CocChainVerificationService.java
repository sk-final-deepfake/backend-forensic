package com.example.demo.service;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.dto.CocChainVerifyResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.EvidenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RQ-HIS-107: 증거별 CoC 해시 체인 검증 API.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CocChainVerificationService {

    private final EvidenceRepository evidenceRepository;
    private final CustodyLogService custodyLogService;

    public CocChainVerifyResponse verifyEvidenceChain(User user, Long evidenceId) {
        Evidence evidence = evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, user.getUserId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거를 찾을 수 없습니다."));

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
