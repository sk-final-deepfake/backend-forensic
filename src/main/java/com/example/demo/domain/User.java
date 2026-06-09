package com.example.demo.domain;

import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "login_id", nullable = false, unique = true)
	private String loginId;

	@Column(name = "email", nullable = false, unique = true)
	private String email;

	@Column(name = "password", nullable = false)
	private String password;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "department", nullable = false)
	private String department;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private UserStatus status;

	@Column(name = "dark_mode", nullable = false)
	private boolean darkMode;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@Builder
	public User(
			String loginId,
			String email,
			String password,
			String name,
			String department,
			UserRole role,
			UserStatus status,
			boolean darkMode,
			LocalDateTime createdAt,
			LocalDateTime updatedAt
	) {
		this.loginId = loginId;
		this.email = email;
		this.password = password;
		this.name = name;
		this.department = department;
		this.role = role;
		this.status = status;
		this.darkMode = darkMode;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public void updateProfile(String loginId, String department) {
		this.loginId = loginId;
		this.department = department;
		this.updatedAt = LocalDateTime.now();
	}

	public void updatePassword(String encodedPassword) {
		this.password = encodedPassword;
		this.updatedAt = LocalDateTime.now();
	}
}
