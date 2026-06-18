package com.example.demo.controller;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.JwtTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class MyPageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EvidenceRepository evidenceRepository;

	@Autowired
	private AnalysisRequestRepository analysisRequestRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String userToken;
	private User testUser;

	@BeforeEach
	void setUp() throws Exception {
		analysisRequestRepository.deleteAll();
		evidenceRepository.deleteAll();
		userRepository.deleteAll();
		testUser = userRepository.save(User.builder()
				.loginId("1111")
				.email("1111@local.dev")
				.password(passwordEncoder.encode("2222"))
				.name("테스트 사용자")
				.organizationType(OrgType.ETC)
				.department("로컬개발팀")
				.role(UserRole.ROLE_USER)
				.status(UserStatus.APPROVED)
				.darkMode(false)
				.build());

		userToken = JwtTestSupport.loginAndGetToken(mockMvc, "1111", "2222");
	}

	@AfterEach
	void tearDown() {
		analysisRequestRepository.deleteAll();
		evidenceRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void getAnalysisHistory_withoutAuth_returnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/mypage/analysis-history"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getAnalysisHistory_withDemoUser_returnsEmptyList() throws Exception {
		mockMvc.perform(get("/api/v1/mypage/analysis-history")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void getAnalysisHistory_withoutCaseNumber_usesCaseNameAsCaseId() throws Exception {
		Evidence evidence = evidenceRepository.save(Evidence.builder()
				.uploaderId(testUser.getUserId())
				.caseName("12121212")
				.fileName("case-name-only.mp4")
				.fileType(FileType.VIDEO)
				.mimeType("video/mp4")
				.fileSize(12L)
				.hashAlgorithm("SHA-256")
				.originalHashValue("a".repeat(64))
				.originalStoragePath("uploads/test/case-name-only.mp4")
				.uploadedAt(LocalDateTime.now())
				.build());

		AnalysisRequest request = new AnalysisRequest();
		request.setEvidenceId(evidence.getEvidenceId());
		request.setRequestedBy(testUser.getUserId());
		request.setStatus(AnalysisStatus.QUEUED);
		request.setRequestedAt(LocalDateTime.now());
		analysisRequestRepository.save(request);

		mockMvc.perform(get("/api/v1/mypage/analysis-history")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].caseId").value("12121212"))
				.andExpect(jsonPath("$.content[0].caseId").value(not(String.valueOf(evidence.getEvidenceId()))))
				.andExpect(jsonPath("$.content[0].caseName").value("12121212"));

		mockMvc.perform(get("/api/v1/cases")
						.param("caseKey", "12121212")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.caseId").value("12121212"))
				.andExpect(jsonPath("$.evidences[0].evidenceId").value(evidence.getEvidenceId()));
	}

	@Test
	void getMyProfile_withDemoUser_returnsProfile() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loginId").value("1111"))
				.andExpect(jsonPath("$.email").value("1111@local.dev"));
	}

	@Test
	void updateMyProfile_withValidRequest_persistsDepartment() throws Exception {
		mockMvc.perform(patch("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "loginId": "1111",
								  "department": "변경된부서",
								  "currentPassword": "2222"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.department").value("변경된부서"));

		mockMvc.perform(get("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(jsonPath("$.department").value("변경된부서"));
	}

	@Test
	void updateMyProfile_withWrongPassword_returnsBadRequest() throws Exception {
		mockMvc.perform(patch("/api/v1/users/me")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "loginId": "1111",
								  "department": "디지털포렌식센터",
								  "currentPassword": "wrong-password"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("INVALID_PASSWORD"));
	}
}
