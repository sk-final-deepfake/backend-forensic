package com.example.demo.security;

import com.example.demo.domain.User;
import com.example.demo.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class UserAuthInterceptor implements HandlerInterceptor {

	private static final String USER_ID_HEADER = "X-User-Id";

	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return true;
		}

		String loginId = request.getHeader(USER_ID_HEADER);
		if (loginId == null || loginId.isBlank()) {
			writeUnauthorized(response, "로그인이 필요합니다.");
			return false;
		}

		User user = userRepository.findByLoginId(loginId.trim())
				.orElse(null);
		if (user == null) {
			writeUnauthorized(response, "사용자를 찾을 수 없습니다.");
			return false;
		}

		UserContext.set(user);
		return true;
	}

	@Override
	public void afterCompletion(
			HttpServletRequest request,
			HttpServletResponse response,
			Object handler,
			Exception ex
	) {
		UserContext.clear();
	}

	private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getWriter(), Map.of(
				"success", false,
				"errorCode", "UNAUTHORIZED",
				"message", message
		));
	}
}
