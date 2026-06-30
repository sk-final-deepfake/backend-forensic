package com.example.demo.controller;

import com.example.demo.dto.BlockchainAnchorStatusResponse;
import com.example.demo.dto.CocChainVerifyResponse;
import com.example.demo.dto.IntegrityVerifyResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.custody.CocChainVerificationService;
import com.example.demo.service.integrity.IntegrityVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Evidence Verification", description = "증거 무결성·CoC·블록체인 검증 API")
@RestController
@RequestMapping({"/api/v1/evidences", "/api/evidences"})
@RequiredArgsConstructor
public class EvidenceVerificationController {

    private final IntegrityVerificationService integrityVerificationService;
    private final CocChainVerificationService cocChainVerificationService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "무결성·서명 검증", description = "RQ-SEC-153 / SK-632: Manifest 서명·CoC 체인·블록체인 해시 검증. 실패 시 409 + errorCode.")
    @GetMapping("/{evidenceId}/integrity/verify")
    public IntegrityVerifyResponse verifyIntegrity(@PathVariable Long evidenceId) {
        return integrityVerificationService.verifyIntegrityOrThrow(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
    }

    @Operation(summary = "CoC 해시 체인 검증", description = "RQ-HIS-107: 증거 감사 로그 해시 체인 무결성 검증")
    @GetMapping("/{evidenceId}/coc/verify")
    public CocChainVerifyResponse verifyCocChain(@PathVariable Long evidenceId) {
        return cocChainVerificationService.verifyEvidenceChain(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
    }

    @Operation(summary = "블록체인 앵커 상태", description = "RQ-DTL-078: 원본 해시·PDF reportHash·머클 루트 앵커 상태 조회")
    @GetMapping("/{evidenceId}/blockchain")
    public BlockchainAnchorStatusResponse blockchainStatus(@PathVariable Long evidenceId) {
        return blockchainAnchorService.getEvidenceAnchorStatus(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
    }
}
