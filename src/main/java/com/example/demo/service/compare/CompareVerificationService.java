package com.example.demo.service.compare;

import com.example.demo.domain.CompareVerification;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CompareVerdict;
import com.example.demo.dto.compare.CompareFileInfoDto;
import com.example.demo.dto.compare.CompareItemDto;
import com.example.demo.dto.compare.CompareOriginalPageResponse;
import com.example.demo.dto.compare.CompareResultResponse;
import com.example.demo.dto.compare.CompareSignatureInfoDto;
import com.example.demo.dto.compare.CompareSummaryDto;
import com.example.demo.dto.compare.CompareVerifyResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.CompareVerificationRepository;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.service.evidence.EvidenceAccessService;
import com.example.demo.service.evidence.FileValidationService;
import com.example.demo.service.evidence.HashService;
import com.example.demo.service.manifest.EvidenceManifestService;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CompareVerificationService {

    private final EvidenceAccessService evidenceAccessService;
    private final EvidenceRepository evidenceRepository;
    private final EvidenceMetadataRepository evidenceMetadataRepository;
    private final CompareVerificationRepository compareVerificationRepository;
    private final FileValidationService fileValidationService;
    private final HashService hashService;
    private final CompareCandidateFileHandler candidateFileHandler;
    private final CompareOriginalProbeExtractor originalProbeExtractor;
    private final CompareItemEvaluator compareItemEvaluator;
    private final CompareVerificationAssembler compareVerificationAssembler;
    private final CompareTrustMetadataAssembler compareTrustMetadataAssembler;
    private final EvidenceManifestService evidenceManifestService;

    @Transactional
    public CompareVerifyResponse verify(User user, Long evidenceId, MultipartFile candidateFile) {
        return verify(user, evidenceId, candidateFile, null);
    }

    public CompareVerifyResponse verify(User user, Long evidenceId, MultipartFile candidateFile, String requestId) {
        Evidence original = evidenceAccessService.requireOwned(user, evidenceId);
        evidenceManifestService.ensureManifest(original);

        fileValidationService.validate(candidateFile);
        EvidenceMetadata originalMetadata = evidenceMetadataRepository.findByEvidenceId(evidenceId).orElse(null);

        Path tempFile = candidateFileHandler.saveTempCandidate(candidateFile);
        try {
            String candidateHash = hashService.generateSha256(tempFile);
            long candidateSize = candidateFile.getSize();
            var originalProbe = originalProbeExtractor.extract(original);
            var candidateProbe = candidateFileHandler.extractProbe(tempFile);

            Evidence candidateEvidence = resolveCandidateByHash(user.getUserId(), candidateHash);
            if (candidateEvidence != null) {
                evidenceManifestService.ensureManifest(candidateEvidence);
            }

            List<CompareItemDto> items = compareItemEvaluator.buildComparisonItems(
                    original, originalMetadata, candidateHash, candidateSize, originalProbe, candidateProbe);
            CompareSummaryDto summary = compareItemEvaluator.summarize(items);
            CompareVerdict verdict = compareItemEvaluator.determineVerdict(
                    items, candidateHash, original.getOriginalHashValue());

            CompareSignatureInfoDto signatureInfo =
                    compareTrustMetadataAssembler.buildSignatureInfo(original, candidateEvidence);

            CompareVerification saved = persistVerification(
                    user.getUserId(),
                    evidenceId,
                    candidateEvidence != null ? candidateEvidence.getEvidenceId() : null,
                    candidateFile.getOriginalFilename(),
                    candidateHash,
                    candidateSize,
                    verdict,
                    summary,
                    items,
                    signatureInfo
            );

            return compareVerificationAssembler.toVerifyResponse(
                    saved,
                    original,
                    items,
                    summary,
                    signatureInfo,
                    compareTrustMetadataAssembler.buildBlockchainInfo(original)
            );
        } finally {
            candidateFileHandler.deleteQuietly(tempFile);
        }
    }

    @Transactional
    public CompareVerifyResponse verifyRegistered(
            User user,
            Long originalEvidenceId,
            Long candidateEvidenceId
    ) {
        if (originalEvidenceId.equals(candidateEvidenceId)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "SAME_EVIDENCE",
                    "서로 다른 두 증거를 선택해 주세요."
            );
        }

        Evidence original = evidenceAccessService.requireOwned(user, originalEvidenceId);
        Evidence candidate = evidenceAccessService.requireOwned(user, candidateEvidenceId);
        evidenceManifestService.ensureManifest(original);
        evidenceManifestService.ensureManifest(candidate);

        EvidenceMetadata originalMetadata = evidenceMetadataRepository
                .findByEvidenceId(originalEvidenceId)
                .orElse(null);
        String candidateHash = candidate.getOriginalHashValue();
        long candidateSize = candidate.getFileSize();
        var originalProbe = originalProbeExtractor.extract(original);
        var candidateProbe = originalProbeExtractor.extract(candidate);

        List<CompareItemDto> items = compareItemEvaluator.buildComparisonItems(
                original,
                originalMetadata,
                candidateHash,
                candidateSize,
                originalProbe,
                candidateProbe
        );
        CompareSummaryDto summary = compareItemEvaluator.summarize(items);
        CompareVerdict verdict = compareItemEvaluator.determineVerdict(
                items,
                candidateHash,
                original.getOriginalHashValue()
        );
        CompareSignatureInfoDto signatureInfo =
                compareTrustMetadataAssembler.buildSignatureInfo(original, candidate);

        CompareVerification saved = persistVerification(
                user.getUserId(),
                originalEvidenceId,
                candidateEvidenceId,
                candidate.getFileName(),
                candidateHash,
                candidateSize,
                verdict,
                summary,
                items,
                signatureInfo
        );

        return compareVerificationAssembler.toVerifyResponse(
                saved,
                original,
                items,
                summary,
                signatureInfo,
                compareTrustMetadataAssembler.buildBlockchainInfo(original)
        );
    }

    @Transactional(readOnly = true)
    public CompareResultResponse getResult(User user, Long compareId) {
        CompareVerification verification = requireOwnedVerification(user, compareId);
        Evidence original = evidenceAccessService.requireOwned(user, verification.getOriginalEvidenceId());
        List<CompareItemDto> items = compareVerificationAssembler.deserializeItems(verification.getResultJson());

        CompareSignatureInfoDto signatureInfo;
        if (verification.getOriginalSignatureStatus() != null
                || verification.getCandidateSignatureStatus() != null) {
            signatureInfo = compareTrustMetadataAssembler.fromSnapshot(
                    verification.getOriginalSignatureStatus(),
                    verification.getCandidateSignatureStatus()
            );
        } else if (verification.getCandidateEvidenceId() != null) {
            Evidence candidate = evidenceRepository.findByEvidenceId(verification.getCandidateEvidenceId())
                    .orElse(null);
            signatureInfo = compareTrustMetadataAssembler.buildSignatureInfo(original, candidate);
        } else {
            signatureInfo = compareTrustMetadataAssembler.buildSignatureInfo(original, null);
        }

        return compareVerificationAssembler.toResultResponse(
                verification,
                items,
                signatureInfo,
                compareTrustMetadataAssembler.buildBlockchainInfo(original)
        );
    }

    @Transactional(readOnly = true)
    public CompareVerification requireOwnedVerification(User user, Long compareId) {
        return compareVerificationRepository.findByCompareIdAndUserId(compareId, user.getUserId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "COMPARE_NOT_FOUND", "비교 검증 결과를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public CompareOriginalPageResponse listOriginals(User user, String search, int page, int size) {
        Page<Evidence> result = evidenceRepository.findCompareOriginals(
                user.getUserId(),
                search,
                PageRequest.of(page, size)
        );

        return CompareOriginalPageResponse.builder()
                .content(result.getContent().stream()
                        .map(compareVerificationAssembler::toOriginalFileInfo)
                        .toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public CompareFileInfoDto getOriginalFileInfo(User user, Long evidenceId) {
        Evidence original = evidenceAccessService.requireReadable(user, evidenceId);
        return compareVerificationAssembler.toOriginalFileInfo(original);
    }

    @Transactional(readOnly = true)
    public CompareFileInfoDto getCandidateFileInfo(User user, Long compareId) {
        CompareVerification verification = requireOwnedVerification(user, compareId);
        return compareVerificationAssembler.toCandidateFileInfo(verification);
    }

    /**
     * FE compare flow sends a client-side cancellation token.
     * Verification runs synchronously, so there is no server-side job to abort.
     */
    public void cancel(String requestId) {
        // acknowledged no-op for API compatibility
    }

    private Evidence resolveCandidateByHash(Long uploaderId, String candidateHash) {
        if (candidateHash == null || candidateHash.isBlank()) {
            return null;
        }
        return evidenceRepository
                .findFirstByUploaderIdAndOriginalHashValueAndDeletedAtIsNullOrderByEvidenceIdDesc(
                        uploaderId, candidateHash)
                .orElse(null);
    }

    private CompareVerification persistVerification(
            Long userId,
            Long evidenceId,
            Long candidateEvidenceId,
            String candidateFileName,
            String candidateHash,
            long candidateSize,
            CompareVerdict verdict,
            CompareSummaryDto summary,
            List<CompareItemDto> items,
            CompareSignatureInfoDto signatureInfo
    ) {
        CompareVerification verification = new CompareVerification();
        verification.setUserId(userId);
        verification.setOriginalEvidenceId(evidenceId);
        verification.setCandidateEvidenceId(candidateEvidenceId);
        verification.setOriginalSignatureStatus(
                signatureInfo.getOriginalStatus() != null ? signatureInfo.getOriginalStatus().name() : null);
        verification.setCandidateSignatureStatus(
                signatureInfo.getCandidateStatus() != null ? signatureInfo.getCandidateStatus().name() : null);
        verification.setCandidateFileName(candidateFileName);
        verification.setCandidateHash(candidateHash);
        verification.setCandidateFileSize(candidateSize);
        verification.setVerdict(verdict);
        verification.setMatchCount(summary.getMatchCount());
        verification.setMismatchCount(summary.getMismatchCount());
        verification.setSkippedCount(summary.getSkippedCount());
        verification.setResultJson(compareVerificationAssembler.serializeItems(items));
        verification.setCreatedAt(LocalDateTime.now());
        return compareVerificationRepository.save(verification);
    }
}
