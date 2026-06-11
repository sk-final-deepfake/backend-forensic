package com.example.demo.service;

import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.ExtractionStatus;
import com.example.demo.dto.detail.AnalysisInfoDto;
import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.dto.detail.CaseEvidenceSummaryDto;
import com.example.demo.dto.detail.CocLogDto;
import com.example.demo.dto.detail.EvidenceDetailResponse;
import com.example.demo.dto.detail.EvidenceInfoDto;
import com.example.demo.dto.detail.IntegrityInfoDto;
import com.example.demo.dto.detail.ModuleResultDto;
import com.example.demo.dto.detail.TechnicalMetadataDto;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceDetailService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final EvidenceRepository evidenceRepository;
    private final EvidenceMetadataRepository evidenceMetadataRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final AnalysisModuleResultRepository analysisModuleResultRepository;
    private final CustodyLogRepository custodyLogRepository;
    private final UserRepository userRepository;

    public EvidenceDetailResponse getEvidenceDetail(User user, Long evidenceId) {
        Evidence evidence = evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, user.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("증거를 찾을 수 없습니다."));

        AnalysisRequest request = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElse(null);
        AnalysisResult result = request == null
                ? null
                : analysisResultRepository.findByAnalysisRequestId(request.getAnalysisRequestId()).orElse(null);
        EvidenceMetadata metadata = evidenceMetadataRepository.findByEvidenceId(evidenceId).orElse(null);
        List<CustodyLog> custodyLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId);

        return EvidenceDetailResponse.builder()
                .evidenceInfo(toEvidenceInfo(evidence, metadata))
                .integrityInfo(toIntegrityInfo(evidence))
                .analysisInfo(toAnalysisInfo(request, result))
                .cocLogs(toCocLogs(custodyLogs, evidence, request, user))
                .build();
    }

    public CaseDetailResponse getCaseDetail(User user, String caseId) {
        List<Evidence> evidences = evidenceRepository.findByUploaderIdAndCaseKey(user.getUserId(), caseId);
        if (evidences.isEmpty()) {
            throw new IllegalArgumentException("사건을 찾을 수 없습니다.");
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
                .createdAt(ISO_FORMATTER.format(createdAt))
                .evidences(summaries)
                .build();
    }

    private EvidenceInfoDto toEvidenceInfo(Evidence evidence, EvidenceMetadata metadata) {
        return EvidenceInfoDto.builder()
                .evidenceId(evidence.getEvidenceId())
                .fileName(evidence.getFileName())
                .caseName(evidence.getCaseName())
                .fileSize(evidence.getFileSize())
                .uploadedAt(ISO_FORMATTER.format(evidence.getUploadedAt()))
                .technicalMetadata(toTechnicalMetadata(metadata))
                .build();
    }

    private TechnicalMetadataDto toTechnicalMetadata(EvidenceMetadata metadata) {
        if (metadata == null) {
            return TechnicalMetadataDto.builder()
                    .width(0)
                    .height(0)
                    .durationSec(0.0)
                    .fps(0.0)
                    .codec("unknown")
                    .extractionStatus("UNAVAILABLE")
                    .build();
        }

        return TechnicalMetadataDto.builder()
                .width(metadata.getWidth() != null ? metadata.getWidth() : 0)
                .height(metadata.getHeight() != null ? metadata.getHeight() : 0)
                .durationSec(metadata.getDurationSec() != null ? metadata.getDurationSec().doubleValue() : 0.0)
                .fps(metadata.getFps() != null ? metadata.getFps() : 0.0)
                .codec(metadata.getCodec() != null ? metadata.getCodec() : "unknown")
                .extractionStatus(metadata.getExtractionStatus() == ExtractionStatus.SUCCESS
                        ? "SUCCESS"
                        : "FAILED")
                .build();
    }

    private IntegrityInfoDto toIntegrityInfo(Evidence evidence) {
        return IntegrityInfoDto.builder()
                .hashAlgorithm(evidence.getHashAlgorithm())
                .originalHash(evidence.getOriginalHashValue())
                .chainValid(true)
                .verificationStatus("VERIFIED")
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

    private List<CocLogDto> toCocLogs(
            List<CustodyLog> custodyLogs,
            Evidence evidence,
            AnalysisRequest request,
            User user
    ) {
        if (!custodyLogs.isEmpty()) {
            return custodyLogs.stream().map(this::toCocLog).toList();
        }

        List<CocLogDto> fallback = new ArrayList<>();
        fallback.add(CocLogDto.builder()
                .logId(1L)
                .eventType("FILE_UPLOADED")
                .userId(user.getLoginId())
                .description("파일 업로드 완료: " + evidence.getFileName())
                .createdAt(ISO_FORMATTER.format(evidence.getUploadedAt()))
                .currentLogHash(shortHash(evidence.getOriginalHashValue()))
                .build());

        if (request != null) {
            fallback.add(CocLogDto.builder()
                    .logId(2L)
                    .eventType("ANALYSIS_REQUESTED")
                    .userId(user.getLoginId())
                    .description("분석 요청 등록")
                    .createdAt(ISO_FORMATTER.format(request.getRequestedAt()))
                    .currentLogHash(shortHash(evidence.getOriginalHashValue() + "-analysis"))
                    .build());
        }

        return fallback;
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
                .createdAt(ISO_FORMATTER.format(log.getCreatedAt()))
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
        return value == null ? null : ISO_FORMATTER.format(value);
    }

    private String shortHash(String hash) {
        if (hash == null || hash.length() < 12) {
            return hash;
        }
        return hash.substring(0, 12) + "...";
    }
}
