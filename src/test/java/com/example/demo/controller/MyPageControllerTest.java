package com.example.demo.controller;

import com.example.demo.domain.User;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
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
	private PasswordEncoder passwordEncoder;

	private String userToken;

	@BeforeEach
	void setUp() throws Exception {
		userRepository.deleteAll();
		userRepository.save(User.builder()
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
