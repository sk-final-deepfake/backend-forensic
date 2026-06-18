package com.example.demo.controller;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.AnalysisResult;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.ListViewMode;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.RiskLevel;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.AnalysisResultRepository;
import com.example.demo.repository.CompareVerificationRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.UserSettingRepository;
import com.example.demo.service.NotificationService;
import com.example.demo.support.JwtTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FeatureApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private CompareVerificationRepository compareVerificationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserSettingRepository userSettingRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String accessToken;
    private User testUser;
    private Evidence evidence;

    @BeforeEach
    void setUp() throws Exception {
        compareVerificationRepository.deleteAll();
        notificationRepository.deleteAll();
        userSettingRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .loginId("1111")
                .email("1111@test.local")
                .password(passwordEncoder.encode("2222"))
                .name("테스트 사용자")
                .organizationType(OrgType.ETC)
                .department("테스트부서")
                .role(UserRole.ROLE_USER)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(testUser.getUserId())
                .caseName("비교 테스트")
                .fileName("sample.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(12L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("abc123def4567890abc123def4567890abc123def4567890abc123def4567890")
                .originalStoragePath("s3://bucket/sample.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());

        accessToken = JwtTestSupport.loginAndGetToken(mockMvc, "1111", "2222");
    }

    @AfterEach
    void tearDown() {
        compareVerificationRepository.deleteAll();
        notificationRepository.deleteAll();
        userSettingRepository.deleteAll();
        analysisResultRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void userSettings_defaultAndUpdate() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateDisplayFormat").value("ISO"))
                .andExpect(jsonPath("$.analysisCompleteNotificationEnabled").value(true))
                .andExpect(jsonPath("$.listViewMode").value("TABLE"));

        mockMvc.perform(patch("/api/v1/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dateDisplayFormat": "KR",
                                  "analysisCompleteNotificationEnabled": false,
                                  "listViewMode": "CARD"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateDisplayFormat").value("KR"))
                .andExpect(jsonPath("$.analysisCompleteNotificationEnabled").value(false))
                .andExpect(jsonPath("$.listViewMode").value("CARD"));
    }

    @Test
    void notifications_listAndMarkRead() throws Exception {
        notificationService.notifyAnalysisCompleted(
                testUser.getUserId(),
                evidence.getEvidenceId(),
                evidence.getFileName()
        );

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications", hasSize(1)))
                .andExpect(jsonPath("$.notifications[0].type").value("ANALYSIS_COMPLETED"))
                .andExpect(jsonPath("$.unreadCount").value(1));

        Long notificationId = notificationRepository.findAll().get(0).getNotificationId();

        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void compare_verifyAndGetResult() throws Exception {
        byte[] content = "fake-video-content".getBytes();
        String hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        MockMultipartFile sameHashFile = new MockMultipartFile(
                "file",
                "candidate.mp4",
                "video/mp4",
                content
        );

        mockMvc.perform(multipart("/api/v1/compare/verify")
                        .file(sameHashFile)
                        .param("evidenceId", String.valueOf(evidence.getEvidenceId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("TAMPERED"))
                .andExpect(jsonPath("$.summary.matchCount").exists())
                .andExpect(jsonPath("$.items").isArray());

        Long compareId = compareVerificationRepository.findAll().get(0).getCompareId();

        mockMvc.perform(get("/api/v1/compare/" + compareId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compareId").value(compareId));

        mockMvc.perform(get("/api/v1/compare/" + compareId + "/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE));
    }

    @Test
    void evidenceAnalysisReport_pdf() throws Exception {
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(testUser.getUserId());
        request.setStatus(AnalysisStatus.COMPLETED);
        request.setProgressPercent(100);
        request.setRequestedAt(LocalDateTime.now());
        request.setStartedAt(LocalDateTime.now());
        request.setCompletedAt(LocalDateTime.now());
        request = analysisRequestRepository.save(request);

        AnalysisResult result = new AnalysisResult();
        result.setAnalysisRequestId(request.getAnalysisRequestId());
        result.setRiskScore(50.0);
        result.setConfidenceScore(0.8);
        result.setRiskLevel(RiskLevel.MEDIUM);
        result.setSummary("test summary");
        result.setAnalyzedAt(LocalDateTime.now());
        analysisResultRepository.save(result);

        mockMvc.perform(get("/api/v1/evidences/" + evidence.getEvidenceId() + "/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE));
    }
}
