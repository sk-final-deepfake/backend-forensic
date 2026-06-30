package com.example.demo.service.evidence;

import com.example.demo.service.analysis.AnalysisInfoAssembler;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.custody.CustodyLogService;
import com.example.demo.service.custody.RecoveryScoreService;
import com.example.demo.service.manifest.EvidenceManifestService;
import com.example.demo.config.EvidenceManifestProperties;
import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.ExtractionStatus;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.dto.IntegrityCheckItem;
import com.example.demo.dto.IntegrityVerifyResponse;
import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.dto.detail.CaseEvidenceSummaryDto;
import com.example.demo.dto.detail.CocLogDto;
import com.example.demo.dto.detail.EvidenceDetailResponse;
import com.example.demo.dto.detail.EvidenceInfoDto;
import com.example.demo.dto.detail.IntegrityInfoDto;
import com.example.demo.dto.detail.ManifestInfoDto;
import com.example.demo.dto.detail.RecoveryScoreDto;
import com.example.demo.dto.detail.SignatureInfoDto;
import com.example.demo.dto.detail.VideoMetadataDto;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.AnalysisStatusMapper;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.EvidenceCaseIdResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceDetailService {

    private final EvidenceRepository evidenceRepository;
    private final EvidenceAccessService evidenceAccessService;
    private final EvidenceMetadataRepository evidenceMetadataRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisModuleResultRepository analysisModuleResultRepository;
    private final CustodyLogRepository custodyLogRepository;
    private final UserRepository userRepository;
    private final CustodyLogService custodyLogService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final EvidenceManifestService evidenceManifestService;
    private final EvidenceManifestProperties evidenceManifestProperties;
    private final RecoveryScoreService recoveryScoreService;
    private final AnalysisInfoAssembler analysisInfoAssembler;

    public EvidenceDetailResponse getEvidenceDetail(User user, Long evidenceId) {
        Evidence evidence = evidenceAccessService.requireOwned(user, evidenceId);
        return buildEvidenceDetail(evidence, null);
    }

    /**
     * RQ-SEC-153: 상세 조회 시 이미 수행한 무결성 검증 결과를 재사용해 중복 검증을 방지한다.
     */
    public EvidenceDetailResponse getEvidenceDetail(Evidence evidence, IntegrityVerifyResponse verification) {
        return buildEvidenceDetail(evidence, verification);
    }

    private EvidenceDetailResponse buildEvidenceDetail(Evidence evidence, IntegrityVerifyResponse verification) {
        Long evidenceId = evidence.getEvidenceId();
        AnalysisRequest request = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElse(null);
        AnalysisResult result = request == null
                ? null
                : analysisResultRepository.findByAnalysisRequestId(request.getAnalysisRequestId()).orElse(null);
        List<AnalysisModuleResult> moduleResults = result == null
                ? List.of()
                : analysisModuleResultRepository.findByAnalysisResultIdOrderByCreatedAtAsc(
                        result.getAnalysisResultId()
                );
        EvidenceMetadata metadata = evidenceMetadataRepository.findByEvidenceId(evidenceId).orElse(null);
        List<CustodyLog> custodyLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId);

        boolean isChainValid = resolveChainValid(verification, evidenceId);
        EvidenceManifest manifest = evidenceManifestService.findByEvidenceId(evidenceId).orElse(null);
        RecoveryScoreDto recovery = recoveryScoreService.calculate(metadata);

        return EvidenceDetailResponse.builder()
                .evidenceInfo(toEvidenceInfo(evidence, metadata))
                .integrityInfo(toIntegrityInfo(evidence, isChainValid, recovery, custodyLogs.size()))
                .manifestInfo(toManifestInfo(evidence, manifest))
                .signatureInfo(toSignatureInfo(manifest, verification))
                .blockchainInfo(blockchainAnchorService.getEvidenceBlockchainInfo(evidence))
                .analysisInfo(analysisInfoAssembler.assemble(request, result, moduleResults))
                .cocLogs(toCocLogs(custodyLogs))
                .build();
    }

    private boolean resolveChainValid(IntegrityVerifyResponse verification, Long evidenceId) {
        if (verification != null) {
            return findCheck(verification, "COC_CHAIN")
                    .map(IntegrityCheckItem::isValid)
                    .orElse(true);
        }
        return custodyLogService.verifyChainIntegrity(CustodyTargetType.EVIDENCE, evidenceId);
    }

    private Optional<IntegrityCheckItem> findCheck(IntegrityVerifyResponse verification, String checkType) {
        if (verification == null || verification.getChecks() == null) {
            return Optional.empty();
        }
        return verification.getChecks().stream()
                .filter(check -> checkType.equals(check.getCheckType()))
                .findFirst();
    }

    public CaseDetailResponse getCaseDetail(User user, String caseId) {
        List<Evidence> evidences = evidenceRepository.findByUploaderIdAndCaseKey(user.getUserId(), caseId);
        if (evidences.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "사건을 찾을 수 없습니다.");
        }

        List<AnalysisRequest> requests = analysisRequestRepository.findByEvidenceIdInOrderByRequestedAtDesc(
                evidences.stream().map(Evidence::getEvidenceId).toList()
        );
        Map<Long, AnalysisRequest> latestByEvidence = new java.util.HashMap<>();
        for (AnalysisRequest request : requests) {
            latestByEvidence.putIfAbsent(request.getEvidenceId(), request);
        }

        String caseName = evidences.stream()
                .map(Evidence::getCaseName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(caseId);
        LocalDateTime createdAt = evidences.stream()
                .map(Evidence::getUploadedAt)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        String aggregateStatus = aggregateStatus(evidences, latestByEvidence);

        List<CaseEvidenceSummaryDto> summaries = evidences.stream()
                .map(evidence -> CaseEvidenceSummaryDto.builder()
                        .evidenceId(evidence.getEvidenceId())
                        .fileName(evidence.getFileName())
                        .mediaType(evidence.getFileType().name())
                        .analysisStatus(toCaseStatus(latestByEvidence.get(evidence.getEvidenceId())))
                        .thumbnailUrl(null)
                        .previewUrl(null)
                        .videoUrl(null)
                        .fileUrl(null)
                        .build())
                .toList();

        return CaseDetailResponse.builder()
                .caseId(caseId)
                .caseName(caseName)
                .status(aggregateStatus)
                .createdAt(ApiDateTimeFormatter.formatUtc(createdAt))
                .evidences(summaries)
                .build();
    }

    private EvidenceInfoDto toEvidenceInfo(Evidence evidence, EvidenceMetadata metadata) {
        return EvidenceInfoDto.builder()
                .evidenceId(evidence.getEvidenceId())
                .fileName(evidence.getFileName())
                .caseName(evidence.getCaseName())
                .caseId(EvidenceCaseIdResolver.resolve(evidence))
                .fileSize(evidence.getFileSize())
                .uploadedAt(ApiDateTimeFormatter.formatUtc(evidence.getUploadedAt()))
                .mediaType(evidence.getFileType().name())
                .fileType(evidence.getFileType().name())
                .technicalMetadata(mapToTypeSpecificMetadata(evidence, metadata))
                .build();
    }

    private VideoMetadataDto mapToTypeSpecificMetadata(Evidence evidence, EvidenceMetadata metadata) {
        if (metadata == null) {
            return VideoMetadataDto.builder()
                    .extractionStatus(ExtractionStatus.FAILED.name())
                    .build();
        }

        String extractionStatus = metadata.getExtractionStatus() == null
                ? ExtractionStatus.FAILED.name()
                : metadata.getExtractionStatus().name();

        return VideoMetadataDto.builder()
                .extractionStatus(extractionStatus)
                .width(metadata.getWidth())
                .height(metadata.getHeight())
                .durationSec(metadata.getDurationSec() != null ? metadata.getDurationSec().doubleValue() : null)
                .fps(metadata.getFps())
                .codec(metadata.getCodec())
                .sampleRate(metadata.getSampleRate())
                .channels(metadata.getChannels())
                .hasAudioTrack(metadata.getSampleRate() != null || metadata.getChannels() != null)
                .build();
    }

    private IntegrityInfoDto toIntegrityInfo(
            Evidence evidence,
            boolean isChainValid,
            RecoveryScoreDto recovery,
            int cocLogCount
    ) {
        return IntegrityInfoDto.builder()
                .hashAlgorithm(evidence.getHashAlgorithm())
                .originalHash(evidence.getOriginalHashValue())
                .copyHash(evidence.getCopyHashValue())
                .copyStatus(evidence.getCopyStatus() != null ? evidence.getCopyStatus().name() : null)
                .chainValid(isChainValid)
                .chainValidAlias(isChainValid)
                .verificationStatus(isChainValid ? "VERIFIED" : "CORRUPTED")
                .recoveryScore(recovery.getRecoveryScore())
                .dataLossPercent(recovery.getDataLossPercent())
                .recoveryGrade(recovery.getGrade())
                .cocLogCount(cocLogCount)
                .cocChainVerified(isChainValid)
                .cocVerificationMessage(isChainValid
                        ? "CoC 해시 체인이 유효합니다."
                        : "CoC 해시 체인 검증에 실패했습니다.")
                .build();
    }

    private ManifestInfoDto toManifestInfo(Evidence evidence, EvidenceManifest manifest) {
        if (manifest == null) {
            return null;
        }
        return ManifestInfoDto.builder()
                .evidenceId(evidence.getEvidenceId())
                .fileId(evidence.getEvidenceId())
                .caseId(EvidenceCaseIdResolver.resolve(evidence))
                .caseNumber(evidence.getCaseNumber())
                .caseName(evidence.getCaseName())
                .fileName(evidence.getFileName())
                .uploadedAt(ApiDateTimeFormatter.formatUtc(evidence.getUploadedAt()))
                .hashAlgorithm(evidence.getHashAlgorithm())
                .originalHash(evidence.getOriginalHashValue())
                .copyHash(evidence.getCopyHashValue())
                .manifestCreatedAt(ApiDateTimeFormatter.formatUtc(manifest.getCreatedAt()))
                .manifestHash(manifest.getManifestHash())
                .issuer(evidenceManifestProperties.getIssuer())
                .build();
    }

    private SignatureInfoDto toSignatureInfo(EvidenceManifest manifest, IntegrityVerifyResponse verification) {
        if (manifest == null) {
            return SignatureInfoDto.builder()
                    .signatureStatus(SignatureStatus.UNSIGNED.name())
                    .build();
        }
        SignatureStatus status = manifest.getSignatureStatus() != null
                ? manifest.getSignatureStatus()
                : SignatureStatus.UNSIGNED;
        Boolean valid = null;
        if (status == SignatureStatus.SIGNED) {
            if (verification != null) {
                valid = findCheck(verification, "SIGNATURE")
                        .map(IntegrityCheckItem::isValid)
                        .orElse(null);
            } else {
                valid = evidenceManifestService.isSignatureValid(manifest);
            }
        }
        return SignatureInfoDto.builder()
                .signatureStatus(status.name())
                .signatureAlgorithm(manifest.getSignatureAlgorithm())
                .signedAt(ApiDateTimeFormatter.formatUtc(manifest.getSignedAt()))
                .signerCertificateSubject(manifest.getSignerCertificateSubject())
                .signatureValid(valid)
                .build();
    }

    private List<CocLogDto> toCocLogs(List<CustodyLog> custodyLogs) {
        return custodyLogs.stream().map(this::toCocLog).toList();
    }

    private CocLogDto toCocLog(CustodyLog log) {
        String actor = userRepository.findById(log.getActorId())
                .map(User::getLoginId)
                .orElse("SYSTEM");

        return CocLogDto.builder()
                .logId(log.getLogId())
                .eventType(log.getActionType())
                .userId(actor)
                .description(log.getReason() != null ? log.getReason() : log.getActionType())
                .createdAt(ApiDateTimeFormatter.formatUtc(log.getCreatedAt()))
                .currentLogHash(log.getCurrentLogHash())
                .build();
    }

    private String aggregateStatus(List<Evidence> evidences, Map<Long, AnalysisRequest> latestByEvidence) {
        String result = "COMPLETED";
        for (Evidence evidence : evidences) {
            String status = toCaseStatus(latestByEvidence.get(evidence.getEvidenceId()));
            result = higherPriorityStatus(result, status);
        }
        return result;
    }

    private String higherPriorityStatus(String current, String candidate) {
        Map<String, Integer> order = Map.of(
                "PROCESSING", 0,
                "PENDING", 1,
                "FAILED", 2,
                "COMPLETED", 3
        );
        return order.get(candidate) < order.get(current) ? candidate : current;
    }

    private String toCaseStatus(AnalysisRequest request) {
        if (request == null) {
            return "PENDING";
        }
        return AnalysisStatusMapper.toApiStatus(request.getStatus());
    }
}
