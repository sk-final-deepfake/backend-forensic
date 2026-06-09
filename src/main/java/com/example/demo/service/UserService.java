package com.example.demo.service;

import com.example.demo.domain.User;
import com.example.demo.dto.user.UpdateUserProfileRequest;
import com.example.demo.dto.user.UserProfileResponse;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

	private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional(readOnly = true)
	public UserProfileResponse getProfile(User user) {
		return toResponse(user);
	}

	public UserProfileResponse updateProfile(User user, UpdateUserProfileRequest request) {
		if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
			throw new IllegalArgumentException("INVALID_PASSWORD");
		}

		if (userRepository.existsByLoginIdAndUserIdNot(request.getLoginId(), user.getUserId())) {
			throw new IllegalArgumentException("DUPLICATE_LOGIN_ID");
		}

		user.updateProfile(request.getLoginId(), request.getDepartment());

		if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
			if (request.getNewPassword().length() < 8) {
				throw new IllegalArgumentException("PASSWORD_TOO_SHORT");
			}
			user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
		}

		return toResponse(user);
	}

	private UserProfileResponse toResponse(User user) {
		return UserProfileResponse.builder()
				.userId(user.getUserId())
				.loginId(user.getLoginId())
				.email(user.getEmail())
				.name(user.getName())
				.department(user.getDepartment())
				.role(user.getRole().name())
				.status(user.getStatus().name())
				.darkMode(user.isDarkMode())
				.createdAt(ISO_FORMATTER.format(user.getCreatedAt()))
				.build();
	}
}
