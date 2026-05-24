package com.vincent.inventoryservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.DelegatingServletOutputStream;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationEntryPointTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    @Test
    void commenceWritesUnauthorizedJson() throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new DelegatingServletOutputStream(body));

        JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint(
                new ObjectMapper().registerModule(new JavaTimeModule())
        );
        entryPoint.commence(request, response, new BadCredentialsException("bad"));

        assertThat(body.toString()).contains("Invalid or expired token");
    }
}
