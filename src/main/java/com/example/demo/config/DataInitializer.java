package com.example.demo.config;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@Profile({"local", "default"})
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final EvidenceRepository evidenceRepository;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final CustodyLogRepository custodyLogRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // 1. 테스트용 사용자 확인 (LocalDevUserInitializer에서 생성됨)
        User demoUser = userRepository.findByLoginIdAndDeletedAtIsNull("1111").orElse(null);
        User adminUser = userRepository.findByLoginIdAndDeletedAtIsNull("3333").orElse(null);

        if (demoUser == null || adminUser == null) {
            return;
        }

        // 2. 이미 데이터가 있으면 중복 생성 방지
        if (!evidenceRepository.findAll().isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 3. Evidence 1: 영상 (분석 완료 상태)
        Evidence videoEvidence = seedEvidence(demoUser, "딥페이크 의심 영상.mp4", FileType.VIDEO, "video/mp4", now.minusDays(1));
        seedAnalysisAndLogs(demoUser, videoEvidence, AnalysisStatus.COMPLETED, now.minusDays(1));

        // 4. Evidence 2: 영상 (분석 중 상태)
        Evidence analyzingVideo = seedEvidence(demoUser, "편집 의심 클립.mp4", FileType.VIDEO, "video/mp4", now.minusHours(5));
        seedAnalysisAndLogs(demoUser, analyzingVideo, AnalysisStatus.ANALYZING, now.minusHours(5));
        
        // 기존 seedCase 로직 보존 (필요 시)
        seedCase(demoUser, "추가 검증 건", AnalysisStatus.QUEUED, now.minusDays(2), 1);
    }

    private Evidence seedEvidence(User user, String fileName, FileType type, String mimeType, LocalDateTime uploadedAt) {
        String hash = UUID.randomUUID().toString().replace("-", "");
        Evidence evidence = Evidence.builder()
                .uploaderId(user.getUserId())
                .caseName("포렌식 테스트 사건")
                .caseNumber("CASE-2026-001")
                .fileName(fileName)
                .fileType(type)
                .mimeType(mimeType)
                .fileSize(1024L * 512)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue(hash)
                .originalStoragePath("original/" + fileName)
                .uploadedAt(uploadedAt)
                .build();
        
        return evidenceRepository.save(evidence);
    }

    private void seedAnalysisAndLogs(User user, Evidence evidence, AnalysisStatus status, LocalDateTime baseTime) {
        // CoC 로그: 파일 업로드
        String log1Hash = createCustodyLog(user, CustodyTargetType.EVIDENCE, evidence.getEvidenceId(), 
                "EVIDENCE_UPLOADED", evidence.getOriginalHashValue(), "INITIAL_HASH", baseTime);
        
        // CoC 로그: 해시 생성
        String log2Hash = createCustodyLog(user, CustodyTargetType.EVIDENCE, evidence.getEvidenceId(), 
                "HASH_CREATED", evidence.getOriginalHashValue(), log1Hash, baseTime.plusMinutes(1));

        // 분석 요청 생성
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(user.getUserId());
        request.setStatus(status);
        request.setRequestedAt(baseTime.plusMinutes(5));
        if (status == AnalysisStatus.COMPLETED) {
            request.setStartedAt(baseTime.plusMinutes(10));
            request.setCompletedAt(baseTime.plusHours(1));
        } else if (status == AnalysisStatus.ANALYZING) {
            request.setStartedAt(baseTime.plusMinutes(10));
        }
        analysisRequestRepository.save(request);

        // CoC 로그: 분석 요청
        createCustodyLog(user, CustodyTargetType.EVIDENCE, evidence.getEvidenceId(), 
                "ANALYSIS_REQUESTED", evidence.getOriginalHashValue(), log2Hash, baseTime.plusMinutes(5));
    }

    private String createCustodyLog(User actor, CustodyTargetType targetType, Long targetId, 
                                   String action, String subjectHash, String prevHash, LocalDateTime time) {
        String currentHash = UUID.randomUUID().toString().replace("-", ""); // 실제 해시 로직 대신 랜덤값
        
        CustodyLog log = new CustodyLog();
        log.setActorId(actor.getUserId());
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setActionType(action);
        log.setSubjectHash(subjectHash);
        log.setPreviousLogHash(prevHash);
        log.setCurrentLogHash(currentHash);
        log.setCreatedAt(time);
        
        custodyLogRepository.save(log);
        return currentHash;
    }

    private void seedCase(User user, String caseName, AnalysisStatus status, LocalDateTime uploadedAt, int evidenceCount) {
        for (int index = 0; index < evidenceCount; index++) {
            Evidence evidence = evidenceRepository.save(Evidence.builder()
                    .uploaderId(user.getUserId())
                    .caseNumber(caseName)
                    .caseName(caseName)
                    .fileName(caseName + "_evidence_" + (index + 1) + ".mp4")
                    .fileType(FileType.VIDEO)
                    .mimeType("video/mp4")
                    .fileSize(1024L * 1024)
                    .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                    .originalHashValue(UUID.randomUUID().toString().replace("-", ""))
                    .originalStoragePath("uploads/seed/" + caseName + "/" + (index + 1))
                    .uploadedAt(uploadedAt.plusMinutes(index))
                    .build());

            if (status != null) {
                AnalysisRequest request = new AnalysisRequest();
                request.setEvidenceId(evidence.getEvidenceId());
                request.setRequestedBy(user.getUserId());
                request.setStatus(status);
                request.setRequestedAt(uploadedAt.plusHours(1));
                request.setStartedAt(status == AnalysisStatus.QUEUED ? null : uploadedAt.plusHours(2));
                request.setCompletedAt(status == AnalysisStatus.COMPLETED ? uploadedAt.plusHours(3) : null);
                analysisRequestRepository.save(request);
            }
        }
    }
}
