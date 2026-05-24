package com.vincent.orderservice.exception;

import com.vincent.orderservice.entity.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private jakarta.servlet.http.HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFound() {
        assertThat(handler.handleNotFound(new OrderNotFoundException("ORD-1")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleInvalidState() {
        assertThat(handler.handleInvalidState(new InvalidOrderStateException("O1", OrderStatus.CONFIRMED, "cancel"))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleBadRequest() {
        assertThat(handler.handleBadRequest(new IllegalArgumentException("bad")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleDuplicate() {
        assertThat(handler.handleDuplicate(new DuplicateOrderRequestException("r1")).getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void handleGeneral() {
        when(request.getRequestURI()).thenReturn("/api/orders");
        assertThat(handler.handleGeneral(new RuntimeException("x"), request).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
