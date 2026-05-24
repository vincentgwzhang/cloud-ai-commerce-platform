package com.vincent.authservice.service;

import com.vincent.authservice.config.JwtProperties;
import com.vincent.authservice.dto.LoginRequest;
import com.vincent.authservice.dto.LoginResponse;
import com.vincent.authservice.dto.RefreshTokenRequest;
import com.vincent.authservice.dto.TokenValidationResponse;
import com.vincent.authservice.entity.User;
import com.vincent.authservice.repository.UserRepository;
import com.vincent.authservice.security.CustomUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtProperties jwtProperties,
            RefreshTokenService refreshTokenService,
            UserRepository userRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
            CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
            User user = userRepository.findByUsername(principal.getUsername())
                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

            String accessToken = jwtService.generateAccessToken(authentication);
            String refreshToken = refreshTokenService.issueForUser(user);

            log.info("Login success for user={}", principal.getUsername());

            return buildLoginResponse(accessToken, refreshToken, principal);
        } catch (BadCredentialsException ex) {
            log.warn("Login failure for user={}", request.username());
            throw ex;
        }
    }

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        RefreshRotationResult rotation = refreshTokenService.rotate(request.refreshToken());
        CustomUserDetails principal = new CustomUserDetails(rotation.user());
        String accessToken = jwtService.generateAccessToken(principal);

        log.info("Token refresh success for user={}", rotation.user().getUsername());

        return buildLoginResponse(accessToken, rotation.refreshToken(), principal);
    }

    public TokenValidationResponse validateToken(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            log.warn("Token validation failed: missing Authorization header");
            throw new JwtException("Missing bearer token");
        }

        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7).trim() : bearerToken.trim();

        try {
            Jwt jwt = jwtService.decodeAndValidate(token);
            String username = jwtService.extractUsername(jwt);
            String role = jwtService.extractRole(jwt);
            log.info("Token validation success for user={}", username);
            return new TokenValidationResponse(true, username, role);
        } catch (JwtException ex) {
            log.warn("Token validation failed: {}", ex.getMessage());
            throw ex;
        }
    }

    private LoginResponse buildLoginResponse(String accessToken, String refreshToken, CustomUserDetails principal) {
        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtProperties.expirationSeconds(),
                refreshToken,
                refreshTokenService.refreshExpirationSeconds(),
                principal.getUsername(),
                principal.getRole()
        );
    }
}
