package com.vincent.authservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Test
    void securityBeansAreConfigured() {
        assertThat(securityFilterChain).isNotNull();
        assertThat(passwordEncoder).isNotNull();
        assertThat(authenticationManager).isNotNull();
        assertThat(passwordEncoder.matches("123456", com.vincent.authservice.support.TestUsers.PASSWORD_HASH))
                .isTrue();
    }
}
