package com.vincent.authservice.service;

import com.vincent.authservice.config.JwtProperties;
import com.vincent.authservice.dto.LoginRequest;
import com.vincent.authservice.dto.LoginResponse;
import com.vincent.authservice.dto.TokenValidationResponse;
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

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            JwtProperties jwtProperties
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
            String accessToken = jwtService.generateAccessToken(authentication);
            CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();

            log.info("Login success for user={}", principal.getUsername());

            return new LoginResponse(
                    accessToken,
                    "Bearer",
                    jwtProperties.expirationSeconds(),
                    principal.getUsername(),
                    principal.getRole()
            );
        } catch (BadCredentialsException ex) {
            log.warn("Login failure for user={}", request.username());
            throw ex;
        }
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
}
