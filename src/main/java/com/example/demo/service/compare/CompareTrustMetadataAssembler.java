package com.example.demo.service.compare;

import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.CompareBlockchainStatus;
import com.example.demo.domain.enums.CompareSignatureStatus;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.dto.compare.CompareBlockchainInfoDto;
import com.example.demo.dto.compare.CompareSignatureInfoDto;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.service.blockchain.BlockchainHashIntegrityEvaluator;
import com.example.demo.service.manifest.EvidenceManifestService;
import com.example.demo.util.ApiDateTimeFormatter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompareTrustMetadataAssembler {

    private final EvidenceManifestService evidenceManifestService;
    private final BlockchainAnchorRepository blockchainAnchorRepository;

    public CompareSignatureInfoDto buildSignatureInfo(Evidence original) {
        return buildSignatureInfo(original, null);
    }

    /**
     * 원본·후보 Manifest 서명 상태를 각각 매핑한다.
     * 후보가 없거나 Manifest가 없으면 candidateStatus=UNSIGNED.
     */
    public CompareSignatureInfoDto buildSignatureInfo(Evidence original, Evidence candidate) {
        Optional<EvidenceManifest> originalManifest =
                evidenceManifestService.findByEvidenceId(original.getEvidenceId());
        CompareSignatureStatus originalStatus = originalManifest
                .map(this::mapSignatureStatus)
                .orElse(CompareSignatureStatus.UNSIGNED);

        CompareSignatureStatus candidateStatus = CompareSignatureStatus.UNSIGNED;
        if (candidate != null) {
            candidateStatus = evidenceManifestService.findByEvidenceId(candidate.getEvidenceId())
                    .map(this::mapSignatureStatus)
                    .orElse(CompareSignatureStatus.UNSIGNED);
        }

        EvidenceManifest displayManifest = originalManifest.orElse(null);
        if (displayManifest == null && candidate != null) {
            displayManifest = evidenceManifestService.findByEvidenceId(candidate.getEvidenceId()).orElse(null);
        }

        CompareSignatureInfoDto.CompareSignatureInfoDtoBuilder builder = CompareSignatureInfoDto.builder()
                .originalStatus(originalStatus)
                .candidateStatus(candidateStatus);

        if (displayManifest != null) {
            builder.algorithm(displayManifest.getSignatureAlgorithm())
                    .signedBy(displayManifest.getSignerCertificateSubject())
                    .signedAt(ApiDateTimeFormatter.formatUtc(displayManifest.getSignedAt()));
        }
        return builder.build();
    }

    public CompareSignatureInfoDto fromSnapshot(String originalStatus, String candidateStatus) {
        return CompareSignatureInfoDto.builder()
                .originalStatus(parseStatus(originalStatus))
                .candidateStatus(parseStatus(candidateStatus))
                .build();
    }

    public CompareBlockchainInfoDto buildBlockchainInfo(Evidence original) {
        Optional<BlockchainAnchor> anchor = blockchainAnchorRepository
                .findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                        original.getEvidenceId(),
                        BlockchainAnchorType.EVIDENCE_HASH
                );

        if (anchor.isEmpty() || anchor.get().getStatus() != BlockchainAnchorStatus.ANCHORED) {
            return CompareBlockchainInfoDto.builder()
                    .status(CompareBlockchainStatus.NOT_ANCHORED)
                    .build();
        }

        BlockchainAnchor anchored = anchor.get();
        CompareBlockchainStatus status = BlockchainHashIntegrityEvaluator.anchoredOriginalHashMatches(original, anchored)
                ? CompareBlockchainStatus.MATCH
                : CompareBlockchainStatus.MISMATCH;

        return CompareBlockchainInfoDto.builder()
                .status(status)
                .network(anchored.getNetwork())
                .txHash(anchored.getTransactionHash())
                .blockNumber(anchored.getBlockNumber())
                .anchoredAt(ApiDateTimeFormatter.formatUtc(anchored.getAnchoredAt()))
                .anchoredHash(anchored.getSubjectHash())
                .build();
    }

    private CompareSignatureStatus mapSignatureStatus(EvidenceManifest manifest) {
        SignatureStatus status = manifest.getSignatureStatus() != null
                ? manifest.getSignatureStatus()
                : SignatureStatus.UNSIGNED;
        return switch (status) {
            case UNSIGNED -> CompareSignatureStatus.UNSIGNED;
            case FAILED -> CompareSignatureStatus.INVALID;
            case SIGNED -> evidenceManifestService.isSignatureValid(manifest)
                    ? CompareSignatureStatus.VALID
                    : CompareSignatureStatus.INVALID;
        };
    }

    private CompareSignatureStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return CompareSignatureStatus.UNSIGNED;
        }
        try {
            return CompareSignatureStatus.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return CompareSignatureStatus.UNSIGNED;
        }
    }
}
