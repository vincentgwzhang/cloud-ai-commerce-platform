package com.vincent.authservice.support;

import com.vincent.authservice.entity.User;

import java.time.Instant;

public final class TestUsers {

    public static final String PASSWORD_PLAIN = "123456";
    public static final String PASSWORD_HASH =
            "$2a$10$uuDx3I721W9gWiUIq0gx6.trwrSkh/zsHLxDQuFJTdM/XbKfti2sm";

    private TestUsers() {
    }

    public static User vincent() {
        return user("vincent", "USER", true);
    }

    public static User disabledUser() {
        return user("disabled", "USER", false);
    }

    public static User user(String username, String role, boolean enabled) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(PASSWORD_HASH);
        user.setRole(role);
        user.setEnabled(enabled);
        user.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        return user;
    }
}
