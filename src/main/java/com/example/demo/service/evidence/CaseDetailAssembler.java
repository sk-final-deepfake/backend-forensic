package com.example.demo.service.evidence;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.CaseProfile;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CaseReviewStatus;
import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.dto.detail.CaseEvidenceSummaryDto;
import com.example.demo.repository.CaseProfileRepository;
import com.example.demo.util.AnalysisStatusMapper;
import com.example.demo.util.ApiDateTimeFormatter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CaseDetailAssembler {

    private final CaseEvidencePresentationService caseEvidencePresentationService;
    private final EvidenceMediaUrlService evidenceMediaUrlService;
    private final CaseProfileRepository caseProfileRepository;

    public CaseDetailResponse assemble(
            User user,
            String caseId,
            List<Evidence> evidences,
            List<AnalysisRequest> analysisRequests
    ) {
        Long uploaderId = evidences.isEmpty() ? user.getUserId() : evidences.get(0).getUploaderId();
        CaseProfile profile = caseProfileRepository.findByUploaderIdAndCaseKey(uploaderId, caseId).orElse(null);
        return assemble(user, uploaderId, caseId, evidences, analysisRequests, profile);
    }

    public CaseDetailResponse assemble(
            User user,
            Long caseOwnerId,
            String caseId,
            List<Evidence> evidences,
            List<AnalysisRequest> analysisRequests,
            CaseProfile profile
    ) {
        Map<Long, AnalysisRequest> latestByEvidence = indexLatestRequests(analysisRequests);
        String caseName = evidences.stream()
                .map(Evidence::getCaseName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(caseId);
        LocalDateTime createdAt = evidences.stream()
                .map(Evidence::getUploadedAt)
                .min(LocalDateTime::compareTo)
                .orElse(profile != null ? profile.getUpdatedAt() : LocalDateTime.now());
        Long representativeEvidenceId = caseEvidencePresentationService
                .resolveRepresentativeEvidenceId(caseOwnerId, caseId, evidences)
                .orElse(null);
        List<Evidence> orderedEvidences = caseEvidencePresentationService.orderForDisplay(evidences);
        List<CaseEvidenceSummaryDto> summaries = orderedEvidences.stream()
                .map(evidence -> toCaseEvidenceSummary(
                        evidence,
                        orderedEvidences,
                        latestByEvidence.get(evidence.getEvidenceId())
                ))
                .toList();

        return CaseDetailResponse.builder()
                .caseId(caseId)
                .caseName(caseName)
                .status(aggregateStatus(evidences, latestByEvidence))
                .createdAt(ApiDateTimeFormatter.formatUtc(createdAt))
                .representativeEvidenceId(representativeEvidenceId)
                .createdBy(String.valueOf(caseOwnerId))
                .assigneeId(profile != null && profile.getAssigneeId() != null
                        ? String.valueOf(profile.getAssigneeId())
                        : String.valueOf(caseOwnerId))
                .reviewerId(profile != null && profile.getReviewerId() != null
                        ? String.valueOf(profile.getReviewerId())
                        : null)
                .reviewStatus(profile != null && profile.getReviewStatus() != null
                        ? profile.getReviewStatus().name()
                        : CaseReviewStatus.NONE.name())
                .evidences(summaries)
                .build();
    }

    public String resolveAggregateStatus(List<Evidence> evidences, List<AnalysisRequest> analysisRequests) {
        return aggregateStatus(evidences, indexLatestRequests(analysisRequests));
    }

    private Map<Long, AnalysisRequest> indexLatestRequests(List<AnalysisRequest> requests) {
        Map<Long, AnalysisRequest> latestByEvidence = new HashMap<>();
        for (AnalysisRequest request : requests) {
            latestByEvidence.putIfAbsent(request.getEvidenceId(), request);
        }
        return latestByEvidence;
    }

    private CaseEvidenceSummaryDto toCaseEvidenceSummary(
            Evidence evidence,
            List<Evidence> caseEvidences,
            AnalysisRequest request
    ) {
        EvidenceMediaUrlService.MediaUrls mediaUrls = evidenceMediaUrlService.resolve(evidence);
        return CaseEvidenceSummaryDto.builder()
                .evidenceId(evidence.getEvidenceId())
                .fileName(evidence.getFileName())
                .displayLabel(caseEvidencePresentationService.resolveDisplayLabel(evidence, caseEvidences))
                .originalFileName(evidence.getFileName())
                .mediaType(evidence.getFileType().name())
                .analysisStatus(toCaseStatus(request))
                .analysisProgress(resolveAnalysisProgress(request))
                .lifecycleStatus(caseEvidencePresentationService.lifecycleStatusName(evidence))
                .role(caseEvidencePresentationService.roleName(evidence))
                .replacementEvidenceId(evidence.getReplacementEvidenceId())
                .excludedReason(evidence.getExcludedReason())
                .thumbnailUrl(mediaUrls.previewUrl())
                .previewUrl(mediaUrls.previewUrl())
                .videoUrl(mediaUrls.videoUrl())
                .fileUrl(mediaUrls.fileUrl())
                .build();
    }

    private String aggregateStatus(List<Evidence> evidences, Map<Long, AnalysisRequest> latestByEvidence) {
        if (evidences.isEmpty()) {
            return "PENDING";
        }
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

    private Integer resolveAnalysisProgress(AnalysisRequest request) {
        if (request == null) {
            return 0;
        }
        if (request.getStatus() == AnalysisStatus.COMPLETED) {
            return 100;
        }
        return request.getProgressPercent();
    }
}
