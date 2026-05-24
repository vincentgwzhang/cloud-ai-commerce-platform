package com.vincent.authservice.service;

import com.vincent.authservice.config.JwtProperties;
import com.vincent.authservice.entity.RefreshToken;
import com.vincent.authservice.entity.User;
import com.vincent.authservice.exception.RefreshTokenException;
import com.vincent.authservice.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public String issueForUser(User user) {
        refreshTokenRepository.revokeAllActiveByUserId(user.getId());
        return persistNewToken(user);
    }

    @Transactional
    public RefreshRotationResult rotate(String presentedToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenAndRevokedFalse(presentedToken)
                .orElseThrow(() -> new RefreshTokenException("Invalid refresh token"));

        if (stored.getExpiryDate().isBefore(Instant.now())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new RefreshTokenException("Refresh token expired");
        }

        User user = stored.getUser();
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        String newRefreshToken = persistNewToken(user);
        return new RefreshRotationResult(user, newRefreshToken);
    }

    public long refreshExpirationSeconds() {
        return jwtProperties.refreshExpirationSeconds();
    }

    private String persistNewToken(User user) {
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setToken(generateOpaqueToken());
        entity.setExpiryDate(Instant.now().plusSeconds(jwtProperties.refreshExpirationSeconds()));
        refreshTokenRepository.save(entity);
        return entity.getToken();
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
