package com.example.demo.service;

import com.example.demo.domain.User;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import com.example.demo.exception.AuthException;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLoginIdAndDeletedAtIsNull(request.getLoginId())
                .orElseThrow(() -> new AuthException(
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS",
                        "사번 또는 비밀번호가 올바르지 않습니다."
                ));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "사번 또는 비밀번호가 올바르지 않습니다."
            );
        }

        validateApprovedStatus(user);

        String token = jwtTokenProvider.createToken(user);

        return LoginResponse.builder()
                .success(true)
                .token(token)
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .name(user.getName())
                .role(user.getRole())
                .build();
    }

    private void validateApprovedStatus(User user) {
        if (user.getStatus() == UserStatus.APPROVED) {
            return;
        }

        String errorCode = switch (user.getStatus()) {
            case PENDING -> "ACCOUNT_PENDING";
            case REJECTED -> "ACCOUNT_REJECTED";
            case SUSPENDED -> "ACCOUNT_SUSPENDED";
            default -> "ACCOUNT_NOT_APPROVED";
        };

        String message = switch (user.getStatus()) {
            case PENDING -> "관리자 승인 대기 중입니다. 승인 후 로그인할 수 있습니다.";
            case REJECTED -> "가입이 반려되었습니다. 관리자에게 문의해 주세요.";
            case SUSPENDED -> "계정이 정지되었습니다. 관리자에게 문의해 주세요.";
            default -> "로그인할 수 없는 계정 상태입니다.";
        };

        throw new AuthException(HttpStatus.UNAUTHORIZED, errorCode, message);
    }
}
