package com.example.demo.dto.user;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileResponse {

	private Long userId;
	private String loginId;
	private String email;
	private String name;
	private String department;
	private String role;
	private String status;
	private boolean darkMode;
	private String createdAt;
}
