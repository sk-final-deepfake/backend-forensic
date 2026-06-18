package com.example.demo.service;

import com.example.demo.config.EvidenceManifestProperties;
import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.ExtractionStatus;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.dto.detail.AnalysisInfoDto;
import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.dto.detail.CaseEvidenceSummaryDto;
import com.example.demo.dto.detail.CocLogDto;
import com.example.demo.dto.detail.EvidenceDetailResponse;
import com.example.demo.dto.detail.EvidenceInfoDto;
import com.example.demo.dto.detail.IntegrityInfoDto;
import com.example.demo.dto.detail.ManifestInfoDto;
import com.example.demo.dto.detail.ModuleResultDto;
import com.example.demo.dto.detail.SignatureInfoDto;
import com.example.demo.dto.detail.VideoMetadataDto;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.exception.BusinessException;
import com.example.demo.util.ApiDateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceDetailService {

    private final EvidenceRepository evidenceRepository;
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

    public EvidenceDetailResponse getEvidenceDetail(User user, Long evidenceId) {
        Evidence evidence = evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, user.getUserId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거를 찾을 수 없습니다."));

        AnalysisRequest request = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElse(null);
        AnalysisResult result = request == null
                ? null
                : analysisResultRepository.findByAnalysisRequestId(request.getAnalysisRequestId()).orElse(null);
        EvidenceMetadata metadata = evidenceMetadataRepository.findByEvidenceId(evidenceId).orElse(null);
        List<CustodyLog> custodyLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId);

        boolean isChainValid = custodyLogService.verifyChainIntegrity(CustodyTargetType.EVIDENCE, evidenceId);
        EvidenceManifest manifest = evidenceManifestService.findByEvidenceId(evidenceId).orElse(null);

        return EvidenceDetailResponse.builder()
                .evidenceInfo(toEvidenceInfo(evidence, metadata))
                .integrityInfo(toIntegrityInfo(evidence, isChainValid))
                .manifestInfo(toManifestInfo(evidence, manifest))
                .signatureInfo(toSignatureInfo(manifest))
                .blockchainInfo(blockchainAnchorService.getEvidenceBlockchainInfo(evidenceId))
                .analysisInfo(toAnalysisInfo(request, result))
                .cocLogs(toCocLogs(custodyLogs))
                .build();
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

    private IntegrityInfoDto toIntegrityInfo(Evidence evidence, boolean isChainValid) {
        return IntegrityInfoDto.builder()
                .hashAlgorithm(evidence.getHashAlgorithm())
                .originalHash(evidence.getOriginalHashValue())
                .copyHash(evidence.getCopyHashValue())
                .copyStatus(evidence.getCopyStatus() != null ? evidence.getCopyStatus().name() : null)
                .chainValid(isChainValid)
                .chainValidAlias(isChainValid)
                .verificationStatus(isChainValid ? "VERIFIED" : "CORRUPTED")
                .build();
    }

    private ManifestInfoDto toManifestInfo(Evidence evidence, EvidenceManifest manifest) {
        if (manifest == null) {
            return null;
        }
        return ManifestInfoDto.builder()
                .evidenceId(evidence.getEvidenceId())
                .fileId(evidence.getEvidenceId())
                .caseId(resolveCaseId(evidence))
                .caseNumber(evidence.getCaseNumber())
                .caseName(evidence.getCaseName())
                .fileName(evidence.getFileName())
                .uploadedAt(formatDateTime(evidence.getUploadedAt()))
                .hashAlgorithm(evidence.getHashAlgorithm())
                .originalHash(evidence.getOriginalHashValue())
                .copyHash(evidence.getCopyHashValue())
                .manifestCreatedAt(formatDateTime(manifest.getCreatedAt()))
                .manifestHash(manifest.getManifestHash())
                .issuer(evidenceManifestProperties.getIssuer())
                .build();
    }

    private SignatureInfoDto toSignatureInfo(EvidenceManifest manifest) {
        if (manifest == null) {
            return SignatureInfoDto.builder()
                    .signatureStatus(SignatureStatus.UNSIGNED.name())
                    .build();
        }
        SignatureStatus status = manifest.getSignatureStatus() != null
                ? manifest.getSignatureStatus()
                : SignatureStatus.UNSIGNED;
        Boolean valid = status == SignatureStatus.SIGNED
                ? evidenceManifestService.isSignatureValid(manifest)
                : null;
        return SignatureInfoDto.builder()
                .signatureStatus(status.name())
                .signatureAlgorithm(manifest.getSignatureAlgorithm())
                .signedAt(formatDateTime(manifest.getSignedAt()))
                .signerCertificateSubject(manifest.getSignerCertificateSubject())
                .signatureValid(valid)
                .build();
    }

    private AnalysisInfoDto toAnalysisInfo(AnalysisRequest request, AnalysisResult result) {
        if (request == null) {
            return AnalysisInfoDto.builder()
                    .status("PENDING")
                    .requestedAt(null)
                    .completedAt(null)
                    .riskScore(null)
                    .confidenceScore(null)
                    .riskLevel(null)
                    .summary("아직 분석이 요청되지 않았습니다.")
                    .moduleResults(List.of())
                    .build();
        }

        String status = toDetailStatus(request.getStatus());
        if (result == null) {
            return AnalysisInfoDto.builder()
                    .status(status)
                    .requestedAt(formatDateTime(request.getRequestedAt()))
                    .completedAt(formatDateTime(request.getCompletedAt()))
                    .riskScore(null)
                    .confidenceScore(null)
                    .riskLevel(null)
                    .summary(pendingSummary(status))
                    .moduleResults(List.of())
                    .build();
        }

        List<AnalysisModuleResult> moduleResults = analysisModuleResultRepository
                .findByAnalysisResultIdOrderByCreatedAtAsc(result.getAnalysisResultId());

        return AnalysisInfoDto.builder()
                .status(status)
                .requestedAt(formatDateTime(request.getRequestedAt()))
                .completedAt(formatDateTime(result.getAnalyzedAt()))
                .riskScore(result.getRiskScore())
                .confidenceScore(result.getConfidenceScore())
                .riskLevel(result.getRiskLevel() != null ? result.getRiskLevel().name() : null)
                .summary(result.getSummary() != null ? result.getSummary() : "분석이 완료되었습니다.")
                .moduleResults(moduleResults.stream().map(this::toModuleResult).toList())
                .build();
    }

    private ModuleResultDto toModuleResult(AnalysisModuleResult moduleResult) {
        return ModuleResultDto.builder()
                .moduleName(moduleResult.getModuleName())
                .detected(Boolean.TRUE.equals(moduleResult.getDetected()))
                .score(moduleResult.getScore() != null ? moduleResult.getScore() : 0.0)
                .details(moduleResult.getDetailsJson() != null ? moduleResult.getDetailsJson() : "{}")
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
                .createdAt(formatDateTime(log.getCreatedAt()))
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
        return switch (request.getStatus()) {
            case QUEUED -> "PENDING";
            case ANALYZING -> "PROCESSING";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
        };
    }

    private String toDetailStatus(AnalysisStatus status) {
        return switch (status) {
            case QUEUED -> "PENDING";
            case ANALYZING -> "PROCESSING";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
        };
    }

    private String pendingSummary(String status) {
        return switch (status) {
            case "PROCESSING" -> "AI 모델이 증거를 분석하고 있습니다.";
            case "FAILED" -> "분석 요청이 실패했습니다.";
            case "COMPLETED" -> "분석이 완료되었습니다.";
            default -> "분석 대기열에 등록되었습니다. AI 모델 연동 후 순차적으로 분석됩니다.";
        };
    }

    private String formatDateTime(LocalDateTime value) {
        return ApiDateTimeFormatter.formatUtc(value);
    }

    private String resolveCaseId(Evidence evidence) {
        if (evidence.getCaseNumber() != null && !evidence.getCaseNumber().isBlank()) {
            return evidence.getCaseNumber();
        }
        if (evidence.getCaseName() != null && !evidence.getCaseName().isBlank()) {
            return evidence.getCaseName();
        }
        return "EVIDENCE-" + evidence.getEvidenceId();
    }

    private String shortHash(String hash) {
        if (hash == null || hash.length() < 12) {
            return hash;
        }
        return hash.substring(0, 12) + "...";
    }
}
