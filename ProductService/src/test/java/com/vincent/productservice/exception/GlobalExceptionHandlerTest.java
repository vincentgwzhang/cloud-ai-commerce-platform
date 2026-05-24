package com.vincent.productservice.exception;

import com.vincent.productservice.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/v1/products/1");
    }

    @Test
    void handleNotFound() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new ProductNotFoundException(99L), request);

        assertStatus(response, HttpStatus.NOT_FOUND, "Product not found: 99");
    }

    @Test
    void handleJwtException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleJwtException(new JwtException("expired"), request);

        assertStatus(response, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
    }

    @Test
    void handleGeneral() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneral(new RuntimeException("boom"), request);

        assertStatus(response, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private void assertStatus(ResponseEntity<ErrorResponse> response, HttpStatus status, String message) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(status.value());
        assertThat(response.getBody().message()).isEqualTo(message);
        assertThat(response.getBody().path()).isEqualTo("/api/v1/products/1");
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}
