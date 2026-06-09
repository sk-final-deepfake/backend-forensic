package com.example.demo.security;

import com.example.demo.domain.User;

public final class UserContext {

	private static final ThreadLocal<User> CURRENT_USER = new ThreadLocal<>();

	private UserContext() {
	}

	public static void set(User user) {
		CURRENT_USER.set(user);
	}

	public static User get() {
		return CURRENT_USER.get();
	}

	public static void clear() {
		CURRENT_USER.remove();
	}
}
