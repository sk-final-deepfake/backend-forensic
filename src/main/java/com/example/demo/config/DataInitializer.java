package com.example.demo.config;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

	private final UserRepository userRepository;
	private final EvidenceRepository evidenceRepository;
	private final AnalysisRequestRepository analysisRequestRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public void run(String... args) {
		if (userRepository.findByLoginId("1111").isPresent()) {
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		User demoUser = userRepository.save(User.builder()
				.loginId("1111")
				.email("kim@forenshield.go.kr")
				.password(passwordEncoder.encode("2222"))
				.name("김포렌식")
				.department("디지털포렌식센터")
				.role(UserRole.ROLE_USER)
				.status(UserStatus.APPROVED)
				.darkMode(false)
				.createdAt(now.minusDays(30))
				.updatedAt(now.minusDays(30))
				.build());

		seedCase(
				demoUser,
				"가세연 녹취록 딥페이크 의혹 사건",
				AnalysisStatus.ANALYZING,
				now.minusDays(1),
				2
		);
		seedCase(
				demoUser,
				"CCTV 영상 위변조 검증 요청",
				AnalysisStatus.COMPLETED,
				now.minusDays(4),
				5
		);
		seedCase(
				demoUser,
				"음성 메일 증거 분석",
				null,
				now.minusDays(2),
				1
		);
		seedCase(
				demoUser,
				"인터뷰 클립 진위 확인",
				AnalysisStatus.FAILED,
				now.minusDays(9),
				3
		);
	}

	private void seedCase(
			User user,
			String caseNumber,
			AnalysisStatus status,
			LocalDateTime uploadedAt,
			int evidenceCount
	) {
		for (int index = 0; index < evidenceCount; index++) {
			Evidence evidence = evidenceRepository.save(Evidence.builder()
					.uploaderId(user.getUserId())
					.caseNumber(caseNumber)
					.fileName(caseNumber + "_evidence_" + (index + 1) + ".mp4")
					.fileType(FileType.VIDEO)
					.mimeType("video/mp4")
					.fileSize(1024L * 1024)
					.hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
					.hashValue("a".repeat(64))
					.originalStoragePath("uploads/seed/" + caseNumber + "/" + (index + 1))
					.status(EvidenceStatus.UPLOADED)
					.uploadedAt(uploadedAt.plusMinutes(index))
					.build());

			if (status != null) {
				analysisRequestRepository.save(AnalysisRequest.builder()
						.evidenceId(evidence.getEvidenceId())
						.requestedBy(user.getUserId())
						.status(status)
						.requestedAt(uploadedAt.plusHours(1))
						.startedAt(status == AnalysisStatus.QUEUED ? null : uploadedAt.plusHours(2))
						.completedAt(status == AnalysisStatus.COMPLETED ? uploadedAt.plusHours(3) : null)
						.build());
			}
		}
	}
}
