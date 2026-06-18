package com.example.demo.controller;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.EvidenceStatus;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.service.AnalysisJobEnqueuer;
import com.example.demo.support.JwtTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EvidenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private AnalysisRequestRepository analysisRequestRepository;

    @Autowired
    private CustodyLogRepository custodyLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.example.demo.repository.UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** 테스트 환경에는 AWS 자격증명이 없으므로 S3 업로드는 모킹한다 */
    @MockBean
    private S3Client s3Client;

    @MockBean
    private AnalysisJobEnqueuer analysisJobEnqueuer;

    @Value("${file.upload-dir}")
    private String uploadDir;

    private String accessToken;

    @BeforeEach
    void obtainAccessToken() throws Exception {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        custodyLogRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.builder()
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

        accessToken = JwtTestSupport.loginAndGetToken(mockMvc, "1111", "2222");
    }

    @AfterEach
    void cleanUp() throws Exception {
        custodyLogRepository.deleteAll();
        analysisRequestRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
        Path root = Paths.get(uploadDir);
        if (Files.exists(root)) {
            FileSystemUtils.deleteRecursively(root);
        }
    }

    @Test
    @DisplayName("인증 없이 증거 API 호출 시 401")
    void shouldRejectUnauthorizedUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-file.mp4",
                "video/mp4",
                "Hello, World!".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void shouldUploadFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-file.mp4",
                "video/mp4",
                "Hello, World!".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "테스트 사건")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("파일 업로드 완료"))
                .andExpect(jsonPath("$.fileName").value("test-file.mp4"))
                .andExpect(jsonPath("$.fileSize").value(file.getSize()))
                .andExpect(jsonPath("$.evidenceId").exists())
                .andExpect(jsonPath("$.hashAlgorithm").value("SHA-256"))
                .andExpect(jsonPath("$.hashValue").isString());
    }

    @Test
    void shouldReturnErrorWhenCaseNameIsBlank() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "no-case-name.mp4",
                "video/mp4",
                "No Case Name".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", " ")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("사건명을 입력해 주세요."));
    }

    @Test
    @DisplayName("사건명을 포함하여 파일을 업로드하면 DB에 사건명이 저장되어야 한다")
    void shouldUploadFileWithCaseName() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "case-test.mp4",
                "video/mp4",
                "Case Name Test Data".getBytes()
        );
        String caseName = "2026-서울-0123 딥페이크 유포 사건";

        mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.caseName").value(caseName));

        assertThat(evidenceRepository.findAll())
                .anyMatch(evidence -> caseName.equals(evidence.getCaseName()));
    }

    @Test
    void shouldReturnErrorWhenFileIsEmpty() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "빈 파일 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FILE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("업로드된 파일이 없습니다."));
    }

    @Test
    void shouldUploadImageFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-video.mp4",
                "video/mp4",
                "fake-image-data".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "영상 업로드 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").value("test-video.mp4"))
                .andExpect(jsonPath("$.hashValue").isString());
    }

    @Test
    void shouldReturnBrokenMetadataForInvalidMediaFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "broken-video.mp4",
                "video/mp4",
                "not-a-video".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "깨진 메타데이터 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.metadata").value("깨짐"))
                .andExpect(jsonPath("$.hashValue").isString());
    }

    @Test
    @DisplayName("동일 파일 2회 업로드 시 동일한 SHA-256 해시값을 반환한다")
    void upload_sameFileTwice_returnsSameHash() throws Exception {
        byte[] content = "sample mp4 content for integrity test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile sampleFile = new MockMultipartFile(
                "file",
                "sample.mp4",
                "video/mp4",
                content
        );

        String firstResponse = mockMvc.perform(multipart("/api/v1/evidences/upload").file(sampleFile)
                        .param("caseName", "동일 파일 해시 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashAlgorithm").value("SHA-256"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstHash = extractHashValue(firstResponse);

        MockMultipartFile sameFileAgain = new MockMultipartFile(
                "file",
                "sample.mp4",
                "video/mp4",
                content
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(sameFileAgain)
                        .param("caseName", "동일 파일 해시 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashValue").value(firstHash));
    }

    @Test
    @DisplayName("수정된 파일 업로드 시 다른 SHA-256 해시값을 반환한다")
    void upload_modifiedFile_returnsDifferentHash() throws Exception {
        byte[] originalContent = "original sample mp4 content".getBytes(StandardCharsets.UTF_8);
        byte[] modifiedContent = "original sample mp4 content-modified".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile originalFile = new MockMultipartFile(
                "file",
                "sample.mp4",
                "video/mp4",
                originalContent
        );

        String originalResponse = mockMvc.perform(multipart("/api/v1/evidences/upload").file(originalFile)
                        .param("caseName", "수정 파일 해시 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        MockMultipartFile modifiedFile = new MockMultipartFile(
                "file",
                "sample_modified.mp4",
                "video/mp4",
                modifiedContent
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(modifiedFile)
                        .param("caseName", "수정 파일 해시 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashValue", not(extractHashValue(originalResponse))));
    }

    @Test
    @DisplayName("영상 분석 건수 통계를 조회할 수 있다")
    void shouldReturnMediaStats() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "file",
                "sample-a.mp4",
                "video/mp4",
                "image bytes".getBytes()
        );
        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "sample-b.mp4",
                "video/mp4",
                "video bytes".getBytes()
        );
        MockMultipartFile audioFile = new MockMultipartFile(
                "file",
                "sample-c.mp4",
                "video/mp4",
                "audio bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(imageFile)
                        .param("caseName", "미디어별 건수 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/evidences/upload").file(videoFile)
                        .param("caseName", "미디어별 건수 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/evidences/upload").file(audioFile)
                        .param("caseName", "미디어별 건수 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/evidences/stats").header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAnalysisCount").value(0))
                .andExpect(jsonPath("$.deepfakeDetectedCount").value(0))
                .andExpect(jsonPath("$.completedCount").value(0))
                .andExpect(jsonPath("$.inProgressCount").value(0));

        long imageEvidenceId = evidenceRepository.findAll().stream()
                .filter(evidence -> evidence.getFileName().equals("sample-a.mp4"))
                .findFirst()
                .orElseThrow()
                .getEvidenceId();
        long videoEvidenceId = evidenceRepository.findAll().stream()
                .filter(evidence -> evidence.getFileName().equals("sample-b.mp4"))
                .findFirst()
                .orElseThrow()
                .getEvidenceId();
        long audioEvidenceId = evidenceRepository.findAll().stream()
                .filter(evidence -> evidence.getFileName().equals("sample-c.mp4"))
                .findFirst()
                .orElseThrow()
                .getEvidenceId();

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "2026-서울-0123 딥페이크 유포 사건",
                                  "evidenceIds": [%d, %d, %d]
                                }
                                """.formatted(imageEvidenceId, videoEvidenceId, audioEvidenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.startedCount").value(3));

        mockMvc.perform(get("/api/v1/evidences/stats").header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAnalysisCount").value(3))
                .andExpect(jsonPath("$.deepfakeDetectedCount").value(0))
                .andExpect(jsonPath("$.completedCount").value(0))
                .andExpect(jsonPath("$.inProgressCount").value(3));
    }

    @Test
    @DisplayName("업로드 시 사건명이 없고 분석 요청에도 사건명이 없으면 400을 반환한다")
    void startAnalysis_withoutCaseName_returnsBadRequest() throws Exception {
        User user = userRepository.findByLoginIdAndDeletedAtIsNull("1111").orElseThrow();
        Evidence evidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .fileName("analyze-test.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(100L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("c".repeat(64))
                .originalStoragePath("original/analyze-test.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evidenceIds": [%d]
                                }
                                """.formatted(evidence.getEvidenceId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("분석 시작 전 업로드된 증거를 취소하면 소프트 삭제된다")
    void cancelUpload_beforeAnalysis_softDeletesEvidence() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cancel-me.mp4",
                "video/mp4",
                "cancel test".getBytes()
        );

        String responseBody = mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "업로드 취소 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = Long.parseLong(responseBody.replaceAll(".*\"evidenceId\":(\\d+).*", "$1"));

        mockMvc.perform(delete("/api/v1/evidences/{evidenceId}", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isNoContent());

        Evidence evidence = evidenceRepository.findById(evidenceId).orElseThrow();
        assertThat(evidence.getStatus()).isEqualTo(EvidenceStatus.DELETED);
        assertThat(evidence.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("분석 대기 중에는 분석 중단 API로 삭제할 수 있다")
    void cancelAnalysis_whenQueued_succeeds() throws Exception {
        User user = userRepository.findByLoginIdAndDeletedAtIsNull("1111").orElseThrow();
        Evidence queuedEvidence = evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .fileName("queued.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(100L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("a".repeat(64))
                .originalStoragePath("original/queued.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());
        long evidenceId = queuedEvidence.getEvidenceId();

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "큐 대기 테스트",
                                  "evidenceIds": [%d]
                                }
                                """.formatted(evidenceId)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/evidences/{evidenceId}/analysis", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isNoContent());

        Evidence evidence = evidenceRepository.findById(evidenceId).orElseThrow();
        assertThat(evidence.getStatus()).isNotEqualTo(EvidenceStatus.DELETED);
        assertThat(evidence.getDeletedAt()).isNull();
        assertThat(analysisRequestRepository.existsByEvidenceId(evidenceId)).isFalse();
    }

    @Test
    @DisplayName("완료된 분석은 중단할 수 없다")
    void cancelAnalysis_whenCompleted_returnsBadRequest() throws Exception {
        User user = userRepository.findByLoginIdAndDeletedAtIsNull("1111").orElseThrow();
        Evidence evidence = saveEvidenceForCancelPolicy(user, "completed.mp4");
        AnalysisRequest request = saveAnalysisRequestForCancelPolicy(
                evidence,
                user,
                AnalysisStatus.COMPLETED
        );

        mockMvc.perform(delete("/api/v1/evidences/{evidenceId}/analysis", evidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ANALYSIS_NOT_CANCELABLE"));

        assertThat(analysisRequestRepository.findById(request.getAnalysisRequestId()))
                .isPresent()
                .get()
                .extracting(AnalysisRequest::getStatus)
                .isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    @DisplayName("실패한 분석은 중단할 수 없다")
    void cancelAnalysis_whenFailed_returnsBadRequest() throws Exception {
        User user = userRepository.findByLoginIdAndDeletedAtIsNull("1111").orElseThrow();
        Evidence evidence = saveEvidenceForCancelPolicy(user, "failed.mp4");
        AnalysisRequest request = saveAnalysisRequestForCancelPolicy(
                evidence,
                user,
                AnalysisStatus.FAILED
        );

        mockMvc.perform(delete("/api/v1/evidences/{evidenceId}/analysis", evidence.getEvidenceId())
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ANALYSIS_NOT_CANCELABLE"));

        assertThat(analysisRequestRepository.findById(request.getAnalysisRequestId()))
                .isPresent()
                .get()
                .extracting(AnalysisRequest::getStatus)
                .isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    @DisplayName("분석 시작 후에는 업로드 취소가 불가하다")
    void cancelUpload_afterAnalysisStarted_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "locked.mp4",
                "video/mp4",
                "locked test".getBytes()
        );

        String responseBody = mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "취소 불가 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = Long.parseLong(responseBody.replaceAll(".*\"evidenceId\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "취소 불가 테스트",
                                  "evidenceIds": [%d]
                                }
                                """.formatted(evidenceId)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/evidences/{evidenceId}", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ANALYSIS_ALREADY_STARTED"));
    }

    @Test
    @DisplayName("업로드 응답의 해시값이 DB에 저장된다")
    void upload_success_persistsEvidenceWithHash() throws Exception {
        long beforeCount = evidenceRepository.count();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.mp4",
                "video/mp4",
                "sample wav bytes".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/evidences/upload").file(file)
                        .param("caseName", "해시 저장 테스트")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashValue").value(org.hamcrest.Matchers.matchesRegex("[0-9a-f]{64}")));

        assertThat(evidenceRepository.count()).isEqualTo(beforeCount + 1);
        assertThat(evidenceRepository.findAll())
                .allMatch(evidence -> evidence.getHashAlgorithm().equals("SHA-256"))
                .allMatch(evidence -> evidence.getHashValue().length() == 64);
    }

    @Test
    @DisplayName("파일 업로드 성공 시 CoC 로그 3개를 ERD 이벤트명과 해시 체인으로 저장한다")
    void upload_success_recordsCustodyLogs() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "coc-test.mp4",
                "video/mp4",
                "coc test image bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "2026-서울-0123 딥페이크 유포 사건";

        String responseBody = mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.hashValue").value(org.hamcrest.Matchers.matchesRegex("[0-9a-f]{64}")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        long evidenceId = response.get("evidenceId").asLong();
        String hashValue = response.get("hashValue").asText();

        List<CustodyLog> logs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId);

        assertThat(logs).hasSize(3);
        assertThat(logs)
                .extracting(CustodyLog::getActionType)
                .containsExactly("EVIDENCE_UPLOADED", "HASH_CREATED", "METADATA_EXTRACTED");
        assertThat(logs)
                .allSatisfy(log -> {
                    assertThat(log.getTargetType()).isEqualTo(CustodyTargetType.EVIDENCE);
                    assertThat(log.getTargetId()).isEqualTo(evidenceId);
                    assertThat(log.getSubjectHash()).isEqualTo(hashValue);
                    assertThat(log.getStoragePathAtEvent()).isNotBlank();
                    assertThat(log.getCurrentLogHash()).matches("[0-9a-f]{64}");
                });

        assertThat(logs.get(0).getPreviousLogHash()).isNull();
        assertThat(logs.get(1).getPreviousLogHash()).isEqualTo(logs.get(0).getCurrentLogHash());
        assertThat(logs.get(2).getPreviousLogHash()).isEqualTo(logs.get(1).getCurrentLogHash());

        JsonNode uploadPayload = objectMapper.readTree(logs.get(0).getEventPayloadJson());
        assertThat(uploadPayload.get("fileName").asText()).isEqualTo("coc-test.mp4");
        assertThat(uploadPayload.get("fileType").asText()).isEqualTo("VIDEO");
        assertThat(uploadPayload.get("mimeType").asText()).isEqualTo("video/mp4");
        assertThat(uploadPayload.get("fileSize").asLong()).isEqualTo(file.getSize());
        assertThat(uploadPayload.get("caseName").asText()).isEqualTo(caseName);

        JsonNode hashPayload = objectMapper.readTree(logs.get(1).getEventPayloadJson());
        assertThat(hashPayload.get("hashAlgorithm").asText()).isEqualTo("SHA-256");
        assertThat(hashPayload.get("hashValue").asText()).isEqualTo(hashValue);

        JsonNode metadataPayload = objectMapper.readTree(logs.get(2).getEventPayloadJson());
        assertThat(metadataPayload.get("extractionStatus").asText()).isIn("SUCCESS", "FAILED");

        assertThat(custodyLogRepository.findAll())
                .extracting(CustodyLog::getActionType)
                .doesNotContain("FILE_UPLOADED", "ORIGINAL_HASH_CREATED");
    }

    @Test
    @DisplayName("증거 상세 API는 프론트 상세 페이지가 사용하는 필드를 포함한다")
    void getEvidenceDetail_returnsFrontendCompatibleFields() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "detail-page.mp4",
                "video/mp4",
                "detail page image bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "상세 페이지 연동 사건";

        String uploadResponseBody = mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = objectMapper.readTree(uploadResponseBody).get("evidenceId").asLong();

        mockMvc.perform(get("/api/v1/evidences/{evidenceId}/detail", evidenceId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceInfo.evidenceId").value(evidenceId))
                .andExpect(jsonPath("$.evidenceInfo.fileName").value("detail-page.mp4"))
                .andExpect(jsonPath("$.evidenceInfo.caseName").value(caseName))
                .andExpect(jsonPath("$.evidenceInfo.mediaType").value("VIDEO"))
                .andExpect(jsonPath("$.evidenceInfo.fileType").value("VIDEO"))
                .andExpect(jsonPath("$.evidenceInfo.technicalMetadata.extractionStatus").isString())
                .andExpect(jsonPath("$.integrityInfo.chainValid").isBoolean())
                .andExpect(jsonPath("$.integrityInfo.isChainValid").isBoolean())
                .andExpect(jsonPath("$.analysisInfo.status").value("PENDING"))
                .andExpect(jsonPath("$.analysisInfo.moduleResults").isArray())
                .andExpect(jsonPath("$.analysisInfo.moduleResults").isEmpty())
                .andExpect(jsonPath("$.cocLogs").isArray())
                .andExpect(jsonPath("$.cocLogs").isNotEmpty());
    }

    @Test
    @DisplayName("분석 요청 성공 시 ANALYSIS_REQUESTED CoC 로그를 저장하고 중복 요청에는 추가하지 않는다")
    void startAnalysis_success_recordsAnalysisRequestedCustodyLog() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "analysis-coc.mp4",
                "video/mp4",
                "analysis coc image bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "2026-서울-0123 딥페이크 유포 사건";

        String uploadResponseBody = mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = objectMapper.readTree(uploadResponseBody).get("evidenceId").asLong();
        Evidence evidence = evidenceRepository.findById(evidenceId).orElseThrow();
        List<CustodyLog> uploadLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId);

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "%s",
                                  "evidenceIds": [%d]
                                }
                                """.formatted(caseName, evidenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.startedCount").value(1));

        AnalysisRequest analysisRequest = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElseThrow();
        List<CustodyLog> analysisLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                        CustodyTargetType.ANALYSIS_REQUEST,
                        analysisRequest.getAnalysisRequestId()
                );

        assertThat(analysisLogs).hasSize(1);
        CustodyLog log = analysisLogs.get(0);
        assertThat(log.getActionType()).isEqualTo("ANALYSIS_REQUESTED");
        assertThat(log.getTargetType()).isEqualTo(CustodyTargetType.ANALYSIS_REQUEST);
        assertThat(log.getTargetId()).isEqualTo(analysisRequest.getAnalysisRequestId());
        assertThat(analysisRequest.getStatus()).isEqualTo(AnalysisStatus.QUEUED);
        assertThat(log.getActorId()).isEqualTo(userRepository.findByLoginIdAndDeletedAtIsNull("1111")
                .orElseThrow()
                .getUserId());
        assertThat(log.getSubjectHash()).isEqualTo(evidence.getOriginalHashValue());
        assertThat(log.getStoragePathAtEvent()).isEqualTo(evidence.getOriginalStoragePath());
        assertThat(log.getCurrentLogHash()).matches("[0-9a-f]{64}");
        assertThat(log.getPreviousLogHash()).isEqualTo(uploadLogs.get(2).getCurrentLogHash());

        JsonNode payload = objectMapper.readTree(log.getEventPayloadJson());
        assertThat(payload.get("evidenceId").asLong()).isEqualTo(evidenceId);
        assertThat(payload.get("analysisRequestId").asLong()).isEqualTo(analysisRequest.getAnalysisRequestId());
        assertThat(payload.get("status").asText()).isEqualTo("QUEUED");
        assertThat(payload.get("caseName").asText()).isEqualTo(caseName);
        assertThat(payload.get("queueRegistered").asBoolean()).isTrue();
        assertThat(payload.get("queueName").asText()).isEqualTo("forenshield.analysis.queue");

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "%s",
                                  "evidenceIds": [%d]
                                }
                                """.formatted(caseName, evidenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.startedCount").value(0));

        assertThat(analysisRequestRepository.count()).isEqualTo(1);
        assertThat(custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                        CustodyTargetType.ANALYSIS_REQUEST,
                        analysisRequest.getAnalysisRequestId()
                )).hasSize(1);
        assertThat(custodyLogRepository.findAll())
                .extracting(CustodyLog::getActionType)
                .doesNotContain("QUEUE_REGISTERED", "ANALYSIS_STARTED", "ANALYSIS_COMPLETED",
                        "ANALYSIS_COPY_CREATED", "ANALYSIS_FAILED", "ERROR_OCCURRED");
    }

    @Test
    @DisplayName("분석 요청 큐 등록 실패 시 FAILED 상태와 ERROR_OCCURRED CoC 로그를 저장한다")
    void startAnalysis_queuePublishFailure_recordsErrorOccurredCustodyLog() throws Exception {
        doThrow(new RuntimeException("rabbit password=secret token=abc"))
                .when(analysisJobEnqueuer)
                .enqueue(any(), any());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "analysis-queue-fail.mp4",
                "video/mp4",
                "analysis queue fail bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "2026-서울-0456 큐 등록 실패 사건";

        String uploadResponseBody = mockMvc.perform(multipart("/api/v1/evidences/upload")
                        .file(file)
                        .param("caseName", caseName)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = objectMapper.readTree(uploadResponseBody).get("evidenceId").asLong();
        Evidence evidence = evidenceRepository.findById(evidenceId).orElseThrow();

        mockMvc.perform(post("/api/v1/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "%s",
                                  "evidenceIds": [%d]
                                }
                                """.formatted(caseName, evidenceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.startedCount").value(0));

        AnalysisRequest analysisRequest = analysisRequestRepository
                .findTopByEvidenceIdOrderByRequestedAtDesc(evidenceId)
                .orElseThrow();
        assertThat(analysisRequest.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysisRequest.getErrorCode()).isEqualTo("RABBITMQ_PUBLISH_FAILED");

        List<CustodyLog> analysisLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                        CustodyTargetType.ANALYSIS_REQUEST,
                        analysisRequest.getAnalysisRequestId()
                );

        assertThat(analysisLogs).hasSize(1);
        CustodyLog log = analysisLogs.get(0);
        assertThat(log.getActionType()).isEqualTo("ERROR_OCCURRED");
        assertThat(log.getTargetType()).isEqualTo(CustodyTargetType.ANALYSIS_REQUEST);
        assertThat(log.getTargetId()).isEqualTo(analysisRequest.getAnalysisRequestId());
        assertThat(log.getSubjectHash()).isEqualTo(evidence.getOriginalHashValue());
        assertThat(log.getStoragePathAtEvent()).isEqualTo(evidence.getOriginalStoragePath());
        assertThat(log.getCurrentLogHash()).matches("[0-9a-f]{64}");

        JsonNode payload = objectMapper.readTree(log.getEventPayloadJson());
        assertThat(payload.get("step").asText()).isEqualTo("RABBITMQ_PUBLISH");
        assertThat(payload.get("errorCode").asText()).isEqualTo("RABBITMQ_PUBLISH_FAILED");
        assertThat(payload.get("message").asText()).isEqualTo("분석 요청 큐 등록에 실패했습니다.");
        assertThat(payload.get("evidenceId").asLong()).isEqualTo(evidenceId);
        assertThat(payload.get("analysisRequestId").asLong()).isEqualTo(analysisRequest.getAnalysisRequestId());
        assertThat(payload.get("queueName").asText()).isEqualTo("forenshield.analysis.queue");
        assertThat(payload.toString().toLowerCase())
                .doesNotContain("password", "token", "secret");

        assertThat(analysisLogs)
                .extracting(CustodyLog::getActionType)
                .doesNotContain("ANALYSIS_REQUESTED");
        assertThat(custodyLogRepository.findAll())
                .extracting(CustodyLog::getActionType)
                .doesNotContain("QUEUE_REGISTERED", "ANALYSIS_STARTED", "ANALYSIS_COMPLETED",
                        "ANALYSIS_COPY_CREATED");
    }

    private String bearerToken() {
        return "Bearer " + accessToken;
    }

    private String extractHashValue(String responseBody) {
        int index = responseBody.indexOf("\"hashValue\":\"");
        if (index < 0) {
            throw new IllegalStateException("hashValue not found in response: " + responseBody);
        }
        int start = index + "\"hashValue\":\"".length();
        int end = responseBody.indexOf('"', start);
        return responseBody.substring(start, end);
    }

    private Evidence saveEvidenceForCancelPolicy(User user, String fileName) {
        return evidenceRepository.save(Evidence.builder()
                .uploaderId(user.getUserId())
                .fileName(fileName)
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(100L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("b".repeat(64))
                .originalStoragePath("original/" + fileName)
                .uploadedAt(LocalDateTime.now())
                .build());
    }

    private AnalysisRequest saveAnalysisRequestForCancelPolicy(
            Evidence evidence,
            User user,
            AnalysisStatus status
    ) {
        AnalysisRequest request = new AnalysisRequest();
        request.setEvidenceId(evidence.getEvidenceId());
        request.setRequestedBy(user.getUserId());
        request.setStatus(status);
        request.setRequestedAt(LocalDateTime.now());
        request.setProgressPercent(status == AnalysisStatus.COMPLETED ? 100 : 0);
        return analysisRequestRepository.save(request);
    }
}
