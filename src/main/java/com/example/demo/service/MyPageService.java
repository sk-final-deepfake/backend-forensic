package com.example.demo.service;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.dto.mypage.AnalysisHistoryPageResponse;
import com.example.demo.dto.mypage.CaseSummaryResponse;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.util.ApiDateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

	public AnalysisHistoryPageResponse getAnalysisHistory(User user, String sort, int page, int size) {
		List<Evidence> evidences = evidenceRepository
				.findByUploaderIdAndStatusAndDeletedAtIsNullOrderByUploadedAtDesc(
						user.getUserId(), EvidenceStatus.UPLOADED);

		if (evidences.isEmpty()) {
			return emptyPage(page, size);
		}

		List<Long> evidenceIds = evidences.stream().map(Evidence::getEvidenceId).toList();
		Map<Long, AnalysisRequest> latestRequestByEvidence = loadLatestRequests(evidenceIds);

		List<Evidence> analyzedEvidences = evidences.stream()
				.filter(evidence -> latestRequestByEvidence.containsKey(evidence.getEvidenceId()))
				.toList();

		if (analyzedEvidences.isEmpty()) {
			return emptyPage(page, size);
		}

		Map<String, List<Evidence>> grouped = groupByCase(analyzedEvidences);

		List<CaseSummaryResponse> summaries = grouped.entrySet().stream()
				.map(entry -> toCaseSummary(entry.getKey(), entry.getValue(), latestRequestByEvidence))
				.sorted(buildComparator(sort))
				.toList();

		int safeSize = Math.max(size, 1);
		int fromIndex = Math.min(page * safeSize, summaries.size());
		int toIndex = Math.min(fromIndex + safeSize, summaries.size());
		List<CaseSummaryResponse> pageContent = summaries.subList(fromIndex, toIndex);
		int totalPages = summaries.isEmpty() ? 0 : (int) Math.ceil((double) summaries.size() / safeSize);

		return AnalysisHistoryPageResponse.builder()
				.content(pageContent)
				.page(page)
				.size(safeSize)
				.totalElements(summaries.size())
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

	private Map<String, List<Evidence>> groupByCase(List<Evidence> evidences) {
		Map<String, List<Evidence>> grouped = new LinkedHashMap<>();
		for (Evidence evidence : evidences) {
			String caseKey = resolveCaseId(evidence);
			grouped.computeIfAbsent(caseKey, key -> new ArrayList<>()).add(evidence);
		}
		return grouped;
	}

	private CaseSummaryResponse toCaseSummary(
			String caseId,
			List<Evidence> caseEvidences,
			Map<Long, AnalysisRequest> latestRequestByEvidence
	) {
		LocalDateTime createdAt = caseEvidences.stream()
				.map(Evidence::getUploadedAt)
				.min(LocalDateTime::compareTo)
				.orElse(LocalDateTime.now());

		String aggregateStatus = aggregateStatus(caseEvidences, latestRequestByEvidence);

		return CaseSummaryResponse.builder()
				.caseId(caseId)
				.caseName(resolveCaseName(caseId, caseEvidences))
				.status(aggregateStatus)
				.createdAt(ApiDateTimeFormatter.formatUtc(createdAt))
				.evidenceCount(caseEvidences.size())
				.build();
	}

	private String aggregateStatus(List<Evidence> evidences, Map<Long, AnalysisRequest> latestRequestByEvidence) {
		String result = "COMPLETED";

		for (Evidence evidence : evidences) {
			AnalysisRequest request = latestRequestByEvidence.get(evidence.getEvidenceId());
			String evidenceStatus = toCaseStatus(request);
			result = higherPriorityStatus(result, evidenceStatus);
		}

		return result;
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

	private String higherPriorityStatus(String current, String candidate) {
		return STATUS_ORDER.get(candidate) < STATUS_ORDER.get(current) ? candidate : current;
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

	private String resolveCaseName(String caseId, List<Evidence> caseEvidences) {
		return caseEvidences.stream()
				.map(Evidence::getCaseName)
				.filter(name -> name != null && !name.isBlank())
				.findFirst()
				.orElse(caseId);
	}

	private Comparator<CaseSummaryResponse> buildComparator(String sort) {
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
