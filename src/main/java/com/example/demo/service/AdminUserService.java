package com.example.demo.service;

import com.example.demo.domain.User;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.dto.admin.AdminUserItemResponse;
import com.example.demo.dto.admin.AdminUserPageResponse;
import com.example.demo.dto.admin.AdminUserStatusResponse;
import com.example.demo.dto.admin.UpdateAdminUserRequest;
import com.example.demo.exception.AdminException;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final CustodyLogService custodyLogService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AdminUserPageResponse listUsers(String search, String status, int page, int size) {
        UserStatus statusFilter = parseStatusFilter(status);
        String normalizedSearch = search == null ? "" : search.trim();

        Page<User> result = userRepository.findAdminUsers(
                statusFilter,
                normalizedSearch,
                PageRequest.of(page, size)
        );

        return AdminUserPageResponse.builder()
                .content(result.getContent().stream().map(this::toItem).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional
    public AdminUserStatusResponse approve(User admin, Long userId) {
        User target = findActiveUser(userId);
        ensurePending(target, "승인");
        target.updateStatus(UserStatus.APPROVED);
        custodyLogService.recordUserAction(admin, target, "USER_APPROVED", target.getLoginId());
        return toStatusResponse(target, admin.getUserId());
    }

    @Transactional
    public AdminUserStatusResponse reject(User admin, Long userId) {
        User target = findActiveUser(userId);
        ensurePending(target, "반려");
        target.updateStatus(UserStatus.REJECTED);
        custodyLogService.recordUserAction(admin, target, "USER_REJECTED", target.getLoginId());
        return toStatusResponse(target, admin.getUserId());
    }

    @Transactional
    public AdminUserStatusResponse suspend(User admin, Long userId) {
        if (admin.getUserId().equals(userId)) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "CANNOT_SUSPEND_SELF", "본인 계정은 정지할 수 없습니다.");
        }

        User target = findActiveUser(userId);
        if (target.getStatus() == UserStatus.SUSPENDED) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "ALREADY_SUSPENDED", "이미 정지된 계정입니다.");
        }
        if (target.getStatus() != UserStatus.APPROVED) {
            throw new AdminException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_USER_STATUS",
                    "APPROVED 상태의 계정만 정지할 수 있습니다."
            );
        }

        target.updateStatus(UserStatus.SUSPENDED);
        custodyLogService.recordUserAction(admin, target, "USER_SUSPENDED", target.getLoginId());
        return toStatusResponse(target, admin.getUserId());
    }

    @Transactional
    public AdminUserItemResponse updateUser(Long userId, UpdateAdminUserRequest request) {
        User target = findActiveUser(userId);

        if (userRepository.existsByEmailAndUserIdNotAndDeletedAtIsNull(request.getEmail(), target.getUserId())) {
            throw new AdminException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다.");
        }

        target.updateAccountInfo(request.getDisplayName(), request.getEmail(), request.getDepartment());
        return toItem(target);
    }

    @Transactional
    public void resetPassword(User admin, Long userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_SHORT", "비밀번호는 8자 이상이어야 합니다.");
        }

        User target = findActiveUser(userId);
        target.updatePassword(passwordEncoder.encode(newPassword));
        custodyLogService.recordUserAction(admin, target, "USER_PASSWORD_RESET", target.getLoginId());
    }

    @Transactional
    public void deleteUser(User admin, Long userId) {
        User target = findActiveUser(userId);
        target.softDelete();
        custodyLogService.recordUserAction(admin, target, "USER_DELETED", target.getLoginId());
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new AdminException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private void ensurePending(User target, String actionLabel) {
        if (target.getStatus() != UserStatus.PENDING) {
            throw new AdminException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_USER_STATUS",
                    "PENDING 상태의 계정만 " + actionLabel + "할 수 있습니다."
            );
        }
    }

    private UserStatus parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AdminException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "유효하지 않은 상태 값입니다.");
        }
    }

    private AdminUserItemResponse toItem(User user) {
        return AdminUserItemResponse.builder()
                .id(String.valueOf(user.getUserId()))
                .username(user.getLoginId())
                .displayName(user.getName())
                .email(user.getEmail())
                .department(user.getDepartment())
                .joinedAt(user.getCreatedAt().toLocalDate().toString())
                .status(user.getStatus().name())
                .build();
    }

    private AdminUserStatusResponse toStatusResponse(User user, Long processedByUserId) {
        return AdminUserStatusResponse.builder()
                .userId(String.valueOf(user.getUserId()))
                .status(user.getStatus().name())
                .processedByUserId(processedByUserId == null ? null : String.valueOf(processedByUserId))
                .build();
    }
}
