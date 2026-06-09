package com.example.demo.config;

import com.example.demo.domain.User;
import com.example.demo.domain.enums.OrgType;
import com.example.demo.domain.enums.UserRole;
import com.example.demo.domain.enums.UserStatus;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 로컬 H2(file) 개발 DB에 테스트 로그인 계정을 넣습니다.
 * 프론트 mock-auth와 동일: 1111/2222(USER), 3333/4444(ADMIN)
 */
@Component
@Profile({"local", "default", "test"})
@RequiredArgsConstructor
public class LocalDevUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!isH2Database() || userRepository.count() > 0) {
            return;
        }

        userRepository.save(createUser("1111", "2222", "테스트 사용자", UserRole.ROLE_USER));
        userRepository.save(createUser("3333", "4444", "테스트 관리자", UserRole.ROLE_ADMIN));
    }

    private boolean isH2Database() {
        String url = environment.getProperty("spring.datasource.url", "");
        return url.startsWith("jdbc:h2:");
    }

    private User createUser(String loginId, String rawPassword, String name, UserRole role) {
        User user = new User();
        user.setLoginId(loginId);
        user.setEmail(loginId + "@local.dev");
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setName(name);
        user.setOrganizationType(OrgType.ETC);
        user.setDepartment("로컬개발팀");
        user.setRole(role);
        user.setStatus(UserStatus.APPROVED);
        user.setDarkMode(false);
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }
}
