package com.vincent.authservice.exception;

import com.vincent.authservice.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

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
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
    }

    @Test
    void handleBadCredentials() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadCredentials(new BadCredentialsException("bad"), request);

        assertStatus(response, HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @Test
    void handleUserNotFound() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUserNotFound(new UsernameNotFoundException("x"), request);

        assertStatus(response, HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @Test
    void handleJwtException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleJwtException(new JwtException("expired"), request);

        assertStatus(response, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
    }

    @Test
    void handleValidation() throws NoSuchMethodException {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(target, "loginRequest");
        bindingResult.addError(new FieldError("loginRequest", "username", "username is required"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                null,
                bindingResult
        );

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertStatus(response, HttpStatus.BAD_REQUEST, "username is required");
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
        assertThat(response.getBody().path()).isEqualTo("/api/v1/auth/login");
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}
