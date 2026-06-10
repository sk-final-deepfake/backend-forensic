package com.example.demo.controller;

import com.example.demo.dto.ErrorResponse;
import com.example.demo.dto.user.UpdateUserProfileRequest;
import com.example.demo.dto.user.UserProfileResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "사용자 프로필 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final AuthUserResolver authUserResolver;

	@Operation(summary = "내 프로필 조회")
	@GetMapping("/me")
	public UserProfileResponse getMyProfile() {
		return userService.getProfile(authUserResolver.requireCurrentUser());
	}

	@Operation(summary = "내 프로필 수정")
	@PatchMapping("/me")
	public ResponseEntity<?> updateMyProfile(@Valid @RequestBody UpdateUserProfileRequest request) {
		try {
			UserProfileResponse response = userService.updateProfile(authUserResolver.requireCurrentUser(), request);
			return ResponseEntity.ok(response);
		} catch (IllegalArgumentException e) {
			String errorCode = e.getMessage();
			String message = switch (errorCode) {
				case "INVALID_PASSWORD" -> "현재 비밀번호가 일치하지 않습니다.";
				case "DUPLICATE_LOGIN_ID" -> "이미 사용 중인 사용자 이름입니다.";
				case "PASSWORD_TOO_SHORT" -> "비밀번호는 최소 8자 이상이어야 합니다.";
				default -> "프로필 수정에 실패했습니다.";
			};
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(ErrorResponse.builder()
							.success(false)
							.errorCode(errorCode)
							.message(message)
							.build());
		}
	}
}
