package com.vincent.authservice.service;

import com.vincent.authservice.entity.User;

public record RefreshRotationResult(User user, String refreshToken) {
}
