package com.example.demo.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MyPageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void getAnalysisHistory_withoutAuth_returnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/mypage/analysis-history"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getAnalysisHistory_withDemoUser_returnsSeedCases() throws Exception {
		mockMvc.perform(get("/api/v1/mypage/analysis-history")
						.header("X-User-Id", "1111"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(4))
				.andExpect(jsonPath("$.content[0].caseName").exists());
	}

	@Test
	void getMyProfile_withDemoUser_returnsProfile() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")
						.header("X-User-Id", "1111"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loginId").value("1111"))
				.andExpect(jsonPath("$.email").value("kim@forenshield.go.kr"));
	}

	@Test
	void updateMyProfile_withWrongPassword_returnsBadRequest() throws Exception {
		mockMvc.perform(patch("/api/v1/users/me")
						.header("X-User-Id", "1111")
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
