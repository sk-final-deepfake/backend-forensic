package com.example.demo.service;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.dto.mypage.AnalysisHistoryItemResponse;
import com.example.demo.dto.mypage.AnalysisHistoryPageResponse;
import com.example.demo.dto.mypage.CaseSummaryResponse;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.util.AnalysisStatusMapper;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.EvidenceCaseIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

		List<AnalysisHistoryItemResponse> evidenceItems = evidences.stream()
				.filter(evidence -> latestRequestByEvidence.containsKey(evidence.getEvidenceId()))
				.map(evidence -> toHistoryItem(
						evidence,
						latestRequestByEvidence.get(evidence.getEvidenceId()),
						resultByRequestId.get(latestRequestByEvidence.get(evidence.getEvidenceId()).getAnalysisRequestId())
				))
				.toList();

		if (evidenceItems.isEmpty()) {
			return emptyPage(page, size);
		}

		List<CaseSummaryResponse> caseSummaries = aggregateByCase(evidenceItems).stream()
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

	private List<CaseSummaryResponse> aggregateByCase(List<AnalysisHistoryItemResponse> evidenceItems) {
		Map<String, List<AnalysisHistoryItemResponse>> grouped = evidenceItems.stream()
				.collect(Collectors.groupingBy(AnalysisHistoryItemResponse::getCaseId));

		List<CaseSummaryResponse> summaries = new ArrayList<>();
		for (Map.Entry<String, List<AnalysisHistoryItemResponse>> entry : grouped.entrySet()) {
			List<AnalysisHistoryItemResponse> items = entry.getValue();
			AnalysisHistoryItemResponse representative = items.stream()
					.max(Comparator.comparing(AnalysisHistoryItemResponse::getRequestedAt))
					.orElse(items.get(0));

			String aggregateStatus = items.stream()
					.map(AnalysisHistoryItemResponse::getStatus)
					.reduce(this::higherPriorityStatus)
					.orElse("PENDING");

			String createdAt = items.stream()
					.map(AnalysisHistoryItemResponse::getRequestedAt)
					.min(String::compareTo)
					.orElse(representative.getRequestedAt());

			Double maxRiskScore = items.stream()
					.map(AnalysisHistoryItemResponse::getRiskScore)
					.filter(score -> score != null)
					.max(Double::compareTo)
					.orElse(null);

			summaries.add(CaseSummaryResponse.builder()
					.caseId(entry.getKey())
					.caseName(representative.getCaseName())
					.status(aggregateStatus)
					.createdAt(createdAt)
					.evidenceCount(items.size())
					.representativeFileName(representative.getFileName())
					.riskScore(maxRiskScore)
					.build());
		}
		return summaries;
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
		List<Long> requestIds = new java.util.ArrayList<>();
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

	private AnalysisHistoryItemResponse toHistoryItem(
			Evidence evidence,
			AnalysisRequest request,
			AnalysisResult result
	) {
		AnalysisStatus status = request.getStatus();
		return AnalysisHistoryItemResponse.builder()
				.evidenceId(evidence.getEvidenceId())
				.analysisRequestId(request.getAnalysisRequestId())
				.caseId(EvidenceCaseIdResolver.resolve(evidence))
				.caseName(resolveCaseName(evidence))
				.fileName(evidence.getFileName())
				.requestedAt(ApiDateTimeFormatter.formatUtc(request.getRequestedAt()))
				.completedAt(result != null
						? ApiDateTimeFormatter.formatUtc(result.getAnalyzedAt())
						: ApiDateTimeFormatter.formatUtc(request.getCompletedAt()))
				.status(AnalysisStatusMapper.toApiStatus(status))
				.queueStatus(AnalysisStatusMapper.toQueueStatus(status))
				.riskLevel(result != null && result.getRiskLevel() != null ? result.getRiskLevel().name() : null)
				.riskScore(result != null ? result.getRiskScore() : null)
				.completed(AnalysisStatusMapper.isCompleted(status))
				.build();
	}

	private String resolveCaseName(Evidence evidence) {
		if (evidence.getCaseName() != null && !evidence.getCaseName().isBlank()) {
			return evidence.getCaseName();
		}
		return EvidenceCaseIdResolver.resolve(evidence);
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
