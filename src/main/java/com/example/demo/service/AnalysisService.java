package com.example.demo.service;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.dto.StartAnalysisResponse;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public StartAnalysisResponse startAnalysis(User user, List<Long> evidenceIds, String caseName) {
        if (caseName == null || caseName.isBlank()) {
            throw new IllegalArgumentException("사건명은 필수입니다.");
        }
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("분석할 증거를 선택해 주세요.");
        }

        String trimmedCaseName = caseName.trim();
        List<Evidence> evidences = evidenceRepository
                .findByEvidenceIdInAndUploaderIdAndDeletedAtIsNull(evidenceIds, user.getUserId());

        if (evidences.isEmpty()) {
            throw new IllegalArgumentException("분석할 수 있는 증거를 찾을 수 없습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        List<Long> startedEvidenceIds = new ArrayList<>();

        for (Evidence evidence : evidences) {
            evidence.updateCaseInfo(trimmedCaseName);

            if (analysisRequestRepository.existsByEvidenceId(evidence.getEvidenceId())) {
                continue;
            }

            AnalysisRequest request = new AnalysisRequest();
            request.setEvidenceId(evidence.getEvidenceId());
            request.setRequestedBy(user.getUserId());
            request.setStatus(AnalysisStatus.QUEUED);
            request.setRequestedAt(now);
            analysisRequestRepository.save(request);
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
}
