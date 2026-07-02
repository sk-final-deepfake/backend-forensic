package com.example.demo.service.user;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.dto.mypage.AnalysisHistoryPageResponse;
import com.example.demo.dto.mypage.CaseSummaryResponse;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.service.evidence.CaseEvidencePresentationService;
import com.example.demo.util.AnalysisStatusMapper;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.EvidenceCaseIdResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

	private static final Map<String, Integer> STATUS_ORDER = Map.of(
			"PROCESSING", 0,
			"PENDING", 1,
			"FAILED", 2,
			"COMPLETED", 3
	);

	private final EvidenceRepository evidenceRepository;
	private final AnalysisRequestRepository analysisRequestRepository;
	private final AnalysisResultRepository analysisResultRepository;
	private final CaseEvidencePresentationService caseEvidencePresentationService;

	public AnalysisHistoryPageResponse getAnalysisHistory(User user, String sort, int page, int size) {
		List<Evidence> evidences = evidenceRepository
				.findByUploaderIdAndStatusAndDeletedAtIsNullOrderByUploadedAtDesc(
						user.getUserId(), EvidenceStatus.UPLOADED);

		if (evidences.isEmpty()) {
			return emptyPage(page, size);
		}

		List<Long> evidenceIds = evidences.stream().map(Evidence::getEvidenceId).toList();
		Map<Long, AnalysisRequest> latestRequestByEvidence = loadLatestRequests(evidenceIds);
		Map<Long, AnalysisResult> resultByRequestId = loadResults(latestRequestByEvidence.values());
		Map<String, List<Evidence>> groupedByCase = caseEvidencePresentationService.groupByCaseKey(evidences);
		Map<String, Long> representativeByCase = caseEvidencePresentationService.loadRepresentativeEvidenceIds(
				user,
				new ArrayList<>(groupedByCase.keySet())
		);

		List<CaseSummaryResponse> caseSummaries = groupedByCase.entrySet().stream()
				.map(entry -> toCaseSummary(
						entry.getKey(),
						entry.getValue(),
						latestRequestByEvidence,
						resultByRequestId,
						representativeByCase.get(entry.getKey())
				))
				.sorted(buildCaseComparator(sort))
				.toList();

		int safeSize = Math.max(size, 1);
		int fromIndex = Math.min(page * safeSize, caseSummaries.size());
		int toIndex = Math.min(fromIndex + safeSize, caseSummaries.size());
		List<CaseSummaryResponse> pageContent = caseSummaries.subList(fromIndex, toIndex);
		int totalPages = caseSummaries.isEmpty() ? 0 : (int) Math.ceil((double) caseSummaries.size() / safeSize);

		return AnalysisHistoryPageResponse.builder()
				.content(pageContent)
				.page(page)
				.size(safeSize)
				.totalElements(caseSummaries.size())
				.totalPages(totalPages)
				.build();
	}

	private CaseSummaryResponse toCaseSummary(
			String caseId,
			List<Evidence> caseEvidences,
			Map<Long, AnalysisRequest> latestRequestByEvidence,
			Map<Long, AnalysisResult> resultByRequestId,
			Long representativeEvidenceId
	) {
		List<Evidence> ordered = caseEvidencePresentationService.orderForDisplay(caseEvidences);
		Evidence representativeEvidence = caseEvidencePresentationService.findRepresentativeEvidence(
				ordered,
				representativeEvidenceId
		);
		if (representativeEvidence == null) {
			representativeEvidence = ordered.stream()
					.filter(Evidence::isWorkflowActive)
					.findFirst()
					.orElse(ordered.get(0));
		}

		String aggregateStatus = ordered.stream()
				.map(evidence -> resolveEvidenceStatus(evidence, latestRequestByEvidence))
				.reduce(this::higherPriorityStatus)
				.orElse("PENDING");

		String createdAt = ordered.stream()
				.map(evidence -> ApiDateTimeFormatter.formatUtc(evidence.getUploadedAt()))
				.min(String::compareTo)
				.orElse(ApiDateTimeFormatter.formatUtc(representativeEvidence.getUploadedAt()));

		Double maxRiskScore = ordered.stream()
				.map(evidence -> latestRequestByEvidence.get(evidence.getEvidenceId()))
				.filter(request -> request != null)
				.map(request -> resultByRequestId.get(request.getAnalysisRequestId()))
				.filter(result -> result != null && result.getRiskScore() != null)
				.map(AnalysisResult::getRiskScore)
				.max(Double::compareTo)
				.orElse(null);

		String caseName = representativeEvidence.getCaseName() != null && !representativeEvidence.getCaseName().isBlank()
				? representativeEvidence.getCaseName()
				: caseId;

		return CaseSummaryResponse.builder()
				.caseId(caseId)
				.caseName(caseName)
				.status(aggregateStatus)
				.createdAt(createdAt)
				.evidenceCount(ordered.size())
				.representativeFileName(representativeEvidence.getFileName())
				.representativeEvidenceId(representativeEvidence.getEvidenceId())
				.representativeEvidenceLabel(
						caseEvidencePresentationService.resolveDisplayLabel(representativeEvidence, ordered)
				)
				.riskScore(maxRiskScore)
				.build();
	}

	private String resolveEvidenceStatus(Evidence evidence, Map<Long, AnalysisRequest> latestRequestByEvidence) {
		AnalysisRequest request = latestRequestByEvidence.get(evidence.getEvidenceId());
		if (request == null) {
			return "PENDING";
		}
		return AnalysisStatusMapper.toApiStatus(request.getStatus());
	}

	private String higherPriorityStatus(String current, String candidate) {
		return STATUS_ORDER.get(candidate) < STATUS_ORDER.get(current) ? candidate : current;
	}

	private AnalysisHistoryPageResponse emptyPage(int page, int size) {
		return AnalysisHistoryPageResponse.builder()
				.content(List.of())
				.page(page)
				.size(size)
				.totalElements(0)
				.totalPages(0)
				.build();
	}

	private Map<Long, AnalysisRequest> loadLatestRequests(List<Long> evidenceIds) {
		List<AnalysisRequest> requests = analysisRequestRepository
				.findByEvidenceIdInOrderByRequestedAtDesc(evidenceIds);

		Map<Long, AnalysisRequest> latest = new HashMap<>();
		for (AnalysisRequest request : requests) {
			latest.putIfAbsent(request.getEvidenceId(), request);
		}
		return latest;
	}

	private Map<Long, AnalysisResult> loadResults(Iterable<AnalysisRequest> requests) {
		List<Long> requestIds = new ArrayList<>();
		for (AnalysisRequest request : requests) {
			requestIds.add(request.getAnalysisRequestId());
		}
		if (requestIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, AnalysisResult> resultByRequestId = new HashMap<>();
		for (AnalysisResult result : analysisResultRepository.findByAnalysisRequestIdIn(requestIds)) {
			resultByRequestId.put(result.getAnalysisRequestId(), result);
		}
		return resultByRequestId;
	}

	private Comparator<CaseSummaryResponse> buildCaseComparator(String sort) {
		if ("status".equalsIgnoreCase(sort)) {
			return Comparator
					.comparing((CaseSummaryResponse item) -> STATUS_ORDER.getOrDefault(item.getStatus(), 99))
					.thenComparing(CaseSummaryResponse::getCreatedAt, Comparator.reverseOrder());
		}
		if ("oldest".equalsIgnoreCase(sort)) {
			return Comparator.comparing(CaseSummaryResponse::getCreatedAt);
		}

		return Comparator.comparing(CaseSummaryResponse::getCreatedAt, Comparator.reverseOrder());
	}
}
