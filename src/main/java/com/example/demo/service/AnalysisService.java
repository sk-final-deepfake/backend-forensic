package com.example.demo.service;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.dto.StartAnalysisRequest;
import com.example.demo.dto.StartAnalysisResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final EvidenceRepository evidenceRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final AnalysisJobEnqueuer analysisJobEnqueuer;

    @Transactional
    public StartAnalysisResponse startAnalysis(User user, StartAnalysisRequest request) {
        List<Long> evidenceIds = resolveEvidenceIds(request);
        String trimmedCaseName = resolveCaseName(request.getCaseName(), evidenceIds, user.getUserId());

        List<Evidence> evidences = evidenceRepository
                .findByEvidenceIdInAndUploaderIdAndDeletedAtIsNull(evidenceIds, user.getUserId());

        if (evidences.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "분석할 수 있는 증거를 찾을 수 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> startedEvidenceIds = new ArrayList<>();

        for (Evidence evidence : evidences) {
            evidence.updateCaseInfo(trimmedCaseName);

            if (analysisRequestRepository.existsByEvidenceId(evidence.getEvidenceId())) {
                continue;
            }

            AnalysisRequest analysisRequest = new AnalysisRequest();
            analysisRequest.setEvidenceId(evidence.getEvidenceId());
            analysisRequest.setRequestedBy(user.getUserId());
            analysisRequest.setStatus(AnalysisStatus.QUEUED);
            analysisRequest.setProgressPercent(0);
            analysisRequest.setRequestedAt(now);
            AnalysisRequest savedRequest = analysisRequestRepository.save(analysisRequest);
            analysisJobEnqueuer.enqueue(savedRequest.getAnalysisRequestId(), evidence.getEvidenceId());
            startedEvidenceIds.add(evidence.getEvidenceId());
        }

        return StartAnalysisResponse.builder()
                .success(true)
                .message("분석 요청이 등록되었습니다.")
                .caseName(trimmedCaseName)
                .startedCount(startedEvidenceIds.size())
                .evidenceIds(startedEvidenceIds)
                .build();
    }

    private List<Long> resolveEvidenceIds(StartAnalysisRequest request) {
        if (request.getEvidenceId() != null) {
            return List.of(request.getEvidenceId());
        }
        if (request.getEvidenceIds() != null && !request.getEvidenceIds().isEmpty()) {
            return request.getEvidenceIds();
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "분석할 증거를 선택해 주세요.");
    }

    private String resolveCaseName(String caseName, List<Long> evidenceIds, Long uploaderId) {
        if (caseName != null && !caseName.isBlank()) {
            return caseName.trim();
        }

        List<Evidence> evidences = evidenceRepository
                .findByEvidenceIdInAndUploaderIdAndDeletedAtIsNull(evidenceIds, uploaderId);
        return evidences.stream()
                .map(Evidence::getCaseName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "사건명은 필수입니다."));
    }
}
