package com.example.demo.controller;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.JwtTestSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@AutoConfigureMockMvc
class AdminReviewCaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String orgAdminToken;

    @BeforeEach
    void setUp() throws Exception {
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .loginId("inv-review")
                .email("inv-review@local.dev")
                .password(passwordEncoder.encode("pass1111"))
                .name("분석관")
                .organizationType(OrgType.POLICE)
                .department("사이버수사팀")
                .role(UserRole.ROLE_INVESTIGATOR)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        userRepository.save(User.builder()
                .loginId("adm-review")
                .email("adm-review@local.dev")
                .password(passwordEncoder.encode("pass3333"))
                .name("기관관리자")
                .organizationType(OrgType.POLICE)
                .department("관리자실")
                .role(UserRole.ROLE_ORG_ADMIN)
                .status(UserStatus.APPROVED)
                .darkMode(false)
                .build());

        orgAdminToken = JwtTestSupport.loginAndGetToken(mockMvc, "adm-review", "pass3333");
    }

    @AfterEach
    void tearDown() {
        evidenceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listReviewCases_orgAdminSeesOrganizationCases() throws Exception {
        User investigator = userRepository.findByLoginIdAndDeletedAtIsNull("inv-review").orElseThrow();
        evidenceRepository.save(Evidence.builder()
                .uploaderId(investigator.getUserId())
                .caseName("review-case-01")
                .caseNumber("review-case-01")
                .fileName("sample.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(12L)
                .hashAlgorithm("SHA-256")
                .originalHashValue("a".repeat(64))
                .originalStoragePath("uploads/test/sample.mp4")
                .uploadedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/admin/review-cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].caseId").value("review-case-01"));
    }

    @Test
    void listReviewCases_investigatorForbidden() throws Exception {
        String investigatorToken = JwtTestSupport.loginAndGetToken(mockMvc, "inv-review", "pass1111");

        mockMvc.perform(get("/api/v1/admin/review-cases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + investigatorToken))
                .andExpect(status().isForbidden());
    }
}
