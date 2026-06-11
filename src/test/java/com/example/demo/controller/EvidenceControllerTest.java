package com.example.demo.controller;

import com.example.demo.domain.AnalysisRequest;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.AnalysisStatus;
import com.example.demo.domain.enums.CustodyTargetType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.messaging.AnalysisQueueMessage;
import com.example.demo.messaging.AnalysisQueuePublisher;
import com.example.demo.repository.AnalysisRequestRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
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

    @MockBean
    private S3Client s3Client;

    @MockBean
    private AnalysisQueuePublisher analysisQueuePublisher;

    @Value("${file.upload-dir}")
    private String uploadDir;

    private String accessToken;

    @BeforeEach
    void obtainAccessToken() throws Exception {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(analysisQueuePublisher.queueName()).thenReturn("test.analysis.requests");

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
                "test-file.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "Hello, World!".getBytes()
        );

        mockMvc.perform(multipart("/api/evidences/upload").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void shouldUploadFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-file.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "Hello, World!".getBytes()
        );

        mockMvc.perform(multipart("/api/evidences/upload").file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("파일 업로드 완료"))
                .andExpect(jsonPath("$.fileName").value("test-file.jpg"))
                .andExpect(jsonPath("$.fileSize").value(file.getSize()))
                .andExpect(jsonPath("$.evidenceId").exists())
                .andExpect(jsonPath("$.hashAlgorithm").value("SHA-256"))
                .andExpect(jsonPath("$.hashValue").isString());
    }

    @Test
    @DisplayName("사건명을 포함하여 파일을 업로드하면 DB에 사건명이 저장되어야 한다")
    void shouldUploadFileWithCaseName() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "case-test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "Case Name Test Data".getBytes()
        );
        String caseName = "2026-서울-0123 딥페이크 유포 사건";

        mockMvc.perform(multipart("/api/evidences/upload")
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

        mockMvc.perform(multipart("/api/evidences/upload").file(file)
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
                "test-image.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-image-data".getBytes()
        );

        mockMvc.perform(multipart("/api/evidences/upload").file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").value("test-image.jpg"))
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

        mockMvc.perform(multipart("/api/evidences/upload").file(file)
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

        String firstResponse = mockMvc.perform(multipart("/api/evidences/upload").file(sampleFile)
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

        mockMvc.perform(multipart("/api/evidences/upload").file(sameFileAgain)
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

        String originalResponse = mockMvc.perform(multipart("/api/evidences/upload").file(originalFile)
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

        mockMvc.perform(multipart("/api/evidences/upload").file(modifiedFile)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashValue", not(extractHashValue(originalResponse))));
    }

    @Test
    @DisplayName("미디어별 분석 건수를 조회할 수 있다")
    void shouldReturnMediaStats() throws Exception {
        MockMultipartFile imageFile = new MockMultipartFile(
                "file",
                "photo.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "image bytes".getBytes()
        );
        MockMultipartFile videoFile = new MockMultipartFile(
                "file",
                "clip.mp4",
                "video/mp4",
                "video bytes".getBytes()
        );
        MockMultipartFile audioFile = new MockMultipartFile(
                "file",
                "voice.wav",
                "audio/wav",
                "audio bytes".getBytes()
        );

        mockMvc.perform(multipart("/api/evidences/upload").file(imageFile)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/evidences/upload").file(videoFile)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/evidences/upload").file(audioFile)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/evidences/stats").header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageCount").value(0))
                .andExpect(jsonPath("$.videoCount").value(0))
                .andExpect(jsonPath("$.audioCount").value(0));

        long imageEvidenceId = evidenceRepository.findAll().stream()
                .filter(evidence -> evidence.getFileName().equals("photo.jpg"))
                .findFirst()
                .orElseThrow()
                .getEvidenceId();
        long videoEvidenceId = evidenceRepository.findAll().stream()
                .filter(evidence -> evidence.getFileName().equals("clip.mp4"))
                .findFirst()
                .orElseThrow()
                .getEvidenceId();
        long audioEvidenceId = evidenceRepository.findAll().stream()
                .filter(evidence -> evidence.getFileName().equals("voice.wav"))
                .findFirst()
                .orElseThrow()
                .getEvidenceId();

        mockMvc.perform(post("/api/evidences/analyze")
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

        mockMvc.perform(get("/api/evidences/stats").header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageCount").value(1))
                .andExpect(jsonPath("$.videoCount").value(1))
                .andExpect(jsonPath("$.audioCount").value(1));
    }

    @Test
    @DisplayName("사건명 없이 분석 시작 요청 시 400을 반환한다")
    void startAnalysis_withoutCaseName_returnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "analyze-test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "analyze test".getBytes()
        );

        String responseBody = mockMvc.perform(multipart("/api/evidences/upload").file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = Long.parseLong(responseBody.replaceAll(".*\"evidenceId\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/evidences/analyze")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evidenceIds": [%d]
                                }
                                """.formatted(evidenceId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("업로드 응답의 해시값이 DB에 저장된다")
    void upload_success_persistsEvidenceWithHash() throws Exception {
        long beforeCount = evidenceRepository.count();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.wav",
                "audio/wav",
                "sample wav bytes".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/evidences/upload").file(file)
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
                "coc-test.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "coc test image bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "2026-서울-0123 딥페이크 유포 사건";

        String responseBody = mockMvc.perform(multipart("/api/evidences/upload")
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
        assertThat(uploadPayload.get("fileName").asText()).isEqualTo("coc-test.jpg");
        assertThat(uploadPayload.get("fileType").asText()).isEqualTo("IMAGE");
        assertThat(uploadPayload.get("mimeType").asText()).isEqualTo(MediaType.IMAGE_JPEG_VALUE);
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
    @DisplayName("분석 요청 성공 시 ANALYSIS_REQUESTED CoC 로그를 저장하고 중복 요청에는 추가하지 않는다")
    void startAnalysis_success_recordsAnalysisRequestedCustodyLog() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "analysis-coc.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "analysis coc image bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "2026-서울-0123 딥페이크 유포 사건";

        String uploadResponseBody = mockMvc.perform(multipart("/api/evidences/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = objectMapper.readTree(uploadResponseBody).get("evidenceId").asLong();
        Evidence evidence = evidenceRepository.findById(evidenceId).orElseThrow();
        List<CustodyLog> uploadLogs = custodyLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(CustodyTargetType.EVIDENCE, evidenceId);

        mockMvc.perform(post("/api/evidences/analyze")
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
        assertThat(payload.get("queueName").asText()).isEqualTo("test.analysis.requests");

        mockMvc.perform(post("/api/evidences/analyze")
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
                .when(analysisQueuePublisher)
                .publish(any(AnalysisQueueMessage.class));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "analysis-queue-fail.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "analysis queue fail bytes".getBytes(StandardCharsets.UTF_8)
        );
        String caseName = "2026-서울-0456 큐 등록 실패 사건";

        String uploadResponseBody = mockMvc.perform(multipart("/api/evidences/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long evidenceId = objectMapper.readTree(uploadResponseBody).get("evidenceId").asLong();
        Evidence evidence = evidenceRepository.findById(evidenceId).orElseThrow();

        mockMvc.perform(post("/api/evidences/analyze")
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
        assertThat(payload.get("queueName").asText()).isEqualTo("test.analysis.requests");
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
}
