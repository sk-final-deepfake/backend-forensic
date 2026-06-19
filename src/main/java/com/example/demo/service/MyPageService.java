package com.example.demo.service;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.dto.mypage.AnalysisHistoryItemResponse;
import com.example.demo.dto.mypage.AnalysisHistoryPageResponse;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.util.AnalysisStatusMapper;
import com.example.demo.util.ApiDateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

		List<AnalysisHistoryItemResponse> items = evidences.stream()
				.filter(evidence -> latestRequestByEvidence.containsKey(evidence.getEvidenceId()))
				.map(evidence -> toHistoryItem(
						evidence,
						latestRequestByEvidence.get(evidence.getEvidenceId()),
						resultByRequestId.get(latestRequestByEvidence.get(evidence.getEvidenceId()).getAnalysisRequestId())
				))
				.sorted(buildComparator(sort))
				.toList();

		if (items.isEmpty()) {
			return emptyPage(page, size);
		}

		int safeSize = Math.max(size, 1);
		int fromIndex = Math.min(page * safeSize, items.size());
		int toIndex = Math.min(fromIndex + safeSize, items.size());
		List<AnalysisHistoryItemResponse> pageContent = items.subList(fromIndex, toIndex);
		int totalPages = items.isEmpty() ? 0 : (int) Math.ceil((double) items.size() / safeSize);

		return AnalysisHistoryPageResponse.builder()
				.content(pageContent)
				.page(page)
				.size(safeSize)
				.totalElements(items.size())
				.totalPages(totalPages)
				.build();
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
				.caseId(resolveCaseId(evidence))
				.caseName(resolveCaseName(evidence))
				.fileName(evidence.getFileName())
				.requestedAt(ApiDateTimeFormatter.formatUtc(request.getRequestedAt()))
				.completedAt(result != null
						? ApiDateTimeFormatter.formatUtc(result.getAnalyzedAt())
						: ApiDateTimeFormatter.formatUtc(request.getCompletedAt()))
				.status(AnalysisStatusMapper.toApiStatus(status))
				.queueStatus(AnalysisStatusMapper.toQueueStatus(status))
				.riskLevel(result != null && result.getRiskLevel() != null ? result.getRiskLevel().name() : null)
				.completed(AnalysisStatusMapper.isCompleted(status))
				.build();
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

	private String resolveCaseName(Evidence evidence) {
		if (evidence.getCaseName() != null && !evidence.getCaseName().isBlank()) {
			return evidence.getCaseName();
		}
		return resolveCaseId(evidence);
	}

	private Comparator<AnalysisHistoryItemResponse> buildComparator(String sort) {
		if ("status".equalsIgnoreCase(sort)) {
			return Comparator
					.comparing((AnalysisHistoryItemResponse item) -> STATUS_ORDER.getOrDefault(item.getStatus(), 99))
					.thenComparing(AnalysisHistoryItemResponse::getRequestedAt, Comparator.reverseOrder());
		}
		if ("oldest".equalsIgnoreCase(sort)) {
			return Comparator.comparing(AnalysisHistoryItemResponse::getRequestedAt);
		}

		return Comparator.comparing(AnalysisHistoryItemResponse::getRequestedAt, Comparator.reverseOrder());
	}
}
