package com.vincent.authservice.service;

import com.vincent.authservice.AuthServiceApplication;
import com.vincent.authservice.security.CustomUserDetails;
import com.vincent.authservice.support.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthServiceApplication.class)
@ActiveProfiles("test")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    private Authentication authentication;

    @BeforeEach
    void setUp() {
        CustomUserDetails principal = new CustomUserDetails(TestUsers.vincent());
        authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void generateDecodeAndExtractClaims() {
        String token = jwtService.generateAccessToken(authentication);

        assertThat(token).isNotBlank();

        Jwt jwt = jwtService.decodeAndValidate(token);
        assertThat(jwtService.extractUsername(jwt)).isEqualTo("vincent");
        assertThat(jwtService.extractRole(jwt)).isEqualTo("USER");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getSubject()).isEqualTo("vincent");
    }

    @Test
    void generateAccessTokenFromPrincipal() {
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();

        String token = jwtService.generateAccessToken(principal);

        Jwt jwt = jwtService.decodeAndValidate(token);
        assertThat(jwtService.extractUsername(jwt)).isEqualTo("vincent");
        assertThat(jwtService.extractRole(jwt)).isEqualTo("USER");
    }

    @Test
    void extractRoleReturnsNullWhenRolesMissing() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("u")
                .build();

        assertThat(jwtService.extractRole(jwt)).isNull();
    }
}
