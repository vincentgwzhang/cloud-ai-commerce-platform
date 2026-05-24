package com.vincent.authservice.service;

import com.vincent.authservice.config.JwtProperties;
import com.vincent.authservice.entity.RefreshToken;
import com.vincent.authservice.entity.User;
import com.vincent.authservice.exception.RefreshTokenException;
import com.vincent.authservice.repository.RefreshTokenRepository;
import com.vincent.authservice.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;

    @BeforeEach
    void setUp() {
        user = TestUsers.vincent();
        user.setId(1L);
    }

    @Test
    void issueForUserRevokesExistingAndPersistsNewToken() {
        when(jwtProperties.refreshExpirationSeconds()).thenReturn(3600L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            token.setId(10L);
            return token;
        });

        String token = refreshTokenService.issueForUser(user);

        assertThat(token).isNotBlank();
        verify(refreshTokenRepository).revokeAllActiveByUserId(1L);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().isRevoked()).isFalse();
    }

    @Test
    void rotateRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenAndRevokedFalse("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("missing"))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessage("Invalid refresh token");
    }

    @Test
    void refreshExpirationSecondsDelegatesToJwtProperties() {
        when(jwtProperties.refreshExpirationSeconds()).thenReturn(99L);

        assertThat(refreshTokenService.refreshExpirationSeconds()).isEqualTo(99L);
    }

    @Test
    void rotateRejectsExpiredToken() {
        RefreshToken stored = activeToken("expired-token");
        stored.setExpiryDate(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenAndRevokedFalse("expired-token")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
                .isInstanceOf(RefreshTokenException.class)
                .hasMessage("Refresh token expired");

        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    void rotateRevokesOldTokenAndIssuesNewOne() {
        when(jwtProperties.refreshExpirationSeconds()).thenReturn(3600L);
        RefreshToken stored = activeToken("valid-token");
        when(refreshTokenRepository.findByTokenAndRevokedFalse("valid-token")).thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshRotationResult result = refreshTokenService.rotate("valid-token");

        assertThat(stored.isRevoked()).isTrue();
        assertThat(result.user()).isEqualTo(user);
        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo("valid-token");
    }

    private RefreshToken activeToken(String value) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(value);
        token.setExpiryDate(Instant.now().plusSeconds(600));
        return token;
    }
}
