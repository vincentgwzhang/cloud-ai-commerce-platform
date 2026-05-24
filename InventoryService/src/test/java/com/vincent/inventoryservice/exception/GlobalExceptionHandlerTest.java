package com.vincent.inventoryservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
    }

    @Test
    void handleNotFound() {
        ResponseEntity<?> response = handler.handleNotFound(new InventoryNotFoundException("IPHONE17"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void handleInsufficient() {
        ResponseEntity<?> response = handler.handleInsufficient(
                new InsufficientInventoryException("IPHONE17", 5, 1));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleGeneral() {
        when(request.getRequestURI()).thenReturn("/api/inventory/IPHONE17");
        ResponseEntity<?> response = handler.handleGeneral(new RuntimeException("boom"), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
