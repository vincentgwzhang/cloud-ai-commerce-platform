package com.vincent.productservice.config;

import com.vincent.productservice.security.JwtAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    @Test
    void jwtAuthenticationConverterMapsRolesClaimToRoleAuthorities() {
        SecurityConfig securityConfig = new SecurityConfig(mock(JwtAuthenticationEntryPoint.class));
        Converter<Jwt, AbstractAuthenticationToken> converter = securityConfig.jwtAuthenticationConverter();

        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", "vincent", "roles", List.of("USER", "ADMIN"))
        );

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .contains(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                );
    }
}
