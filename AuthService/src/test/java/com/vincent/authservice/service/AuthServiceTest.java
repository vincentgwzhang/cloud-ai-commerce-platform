package com.vincent.authservice.service;

import com.vincent.authservice.config.JwtProperties;
import com.vincent.authservice.dto.LoginRequest;
import com.vincent.authservice.dto.LoginResponse;
import com.vincent.authservice.dto.TokenValidationResponse;
import com.vincent.authservice.security.CustomUserDetails;
import com.vincent.authservice.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthService authService;

    private Authentication authentication;

    @BeforeEach
    void setUp() {
        CustomUserDetails principal = new CustomUserDetails(TestUsers.vincent());
        authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(jwtProperties.expirationSeconds()).thenReturn(3600L);
    }

    @Test
    void loginSuccess() {
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.generateAccessToken(authentication)).thenReturn("jwt-token");

        LoginResponse response = authService.login(new LoginRequest("vincent", "123456"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.username()).isEqualTo("vincent");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void loginFailureThrowsBadCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("vincent", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void validateTokenWithBearerPrefix() {
        Jwt jwt = mock(Jwt.class);
        when(jwtService.decodeAndValidate("valid-token")).thenReturn(jwt);
        when(jwtService.extractUsername(jwt)).thenReturn("vincent");
        when(jwtService.extractRole(jwt)).thenReturn("USER");

        TokenValidationResponse response = authService.validateToken("Bearer valid-token");

        assertThat(response.valid()).isTrue();
        assertThat(response.username()).isEqualTo("vincent");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void validateTokenWithoutBearerPrefix() {
        Jwt jwt = mock(Jwt.class);
        when(jwtService.decodeAndValidate("raw-token")).thenReturn(jwt);
        when(jwtService.extractUsername(jwt)).thenReturn("vincent");
        when(jwtService.extractRole(jwt)).thenReturn("USER");

        TokenValidationResponse response = authService.validateToken("raw-token");

        assertThat(response.valid()).isTrue();
    }

    @Test
    void validateTokenMissingHeader() {
        assertThatThrownBy(() -> authService.validateToken(null))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Missing bearer token");

        assertThatThrownBy(() -> authService.validateToken("   "))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void validateTokenInvalidJwt() {
        when(jwtService.decodeAndValidate("bad"))
                .thenThrow(new JwtException("invalid"));

        assertThatThrownBy(() -> authService.validateToken("Bearer bad"))
                .isInstanceOf(JwtException.class);

        verify(jwtService).decodeAndValidate("bad");
    }
}
