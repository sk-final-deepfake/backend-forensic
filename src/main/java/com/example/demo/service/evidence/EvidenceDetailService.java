package com.example.demo.service.evidence;

import com.example.demo.service.analysis.AnalysisInfoAssembler;
import com.example.demo.service.custody.RecoveryScoreService;
import com.example.demo.service.manifest.EvidenceManifestService;
import com.example.demo.domain.AnalysisModuleResult;
import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.dto.IntegrityVerifyResponse;
import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.dto.detail.EvidenceDetailResponse;
import com.example.demo.dto.detail.RecoveryScoreDto;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.AnalysisModuleResultRepository;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.util.CaseKeyNormalizer;
import java.util.List;
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
    private final EvidenceManifestService evidenceManifestService;
    private final RecoveryScoreService recoveryScoreService;
    private final CaseDetailAssembler caseDetailAssembler;
    private final CaseAccessService caseAccessService;
    private final EvidenceDetailAssembler evidenceDetailAssembler;

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
        EvidenceManifest manifest = evidenceManifestService.findByEvidenceId(evidenceId).orElse(null);
        RecoveryScoreDto recovery = recoveryScoreService.calculate(metadata);

        return evidenceDetailAssembler.assemble(
                evidence,
                verification,
                metadata,
                request,
                result,
                moduleResults,
                custodyLogs,
                manifest,
                recovery
        );
    }

    public CaseDetailResponse getCaseDetail(User user, String caseKey, String pathCaseId) {
        return getCaseDetail(user, CaseKeyNormalizer.resolveCaseKey(caseKey, pathCaseId));
    }

    public CaseDetailResponse getCaseDetail(User user, String caseId) {
        String normalizedCaseId = CaseKeyNormalizer.requireCaseKey(caseId);
        CaseAccessService.CaseAccessContext context = caseAccessService.requireAccessibleCase(user, normalizedCaseId);
        List<Long> evidenceIds = context.evidences().stream().map(Evidence::getEvidenceId).toList();
        List<AnalysisRequest> requests = analysisRequestRepository.findByEvidenceIdInOrderByRequestedAtDesc(evidenceIds);
        return caseDetailAssembler.assemble(
                user,
                context.uploaderId(),
                normalizedCaseId,
                context.evidences(),
                requests,
                context.profile()
        );
    }
}
