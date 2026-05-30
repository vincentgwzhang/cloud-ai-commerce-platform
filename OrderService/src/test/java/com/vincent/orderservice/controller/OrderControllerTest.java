package com.vincent.orderservice.controller;

import com.vincent.orderservice.dto.OrderResponse;
import com.vincent.orderservice.entity.OrderStatus;
import com.vincent.orderservice.service.OrderConcurrencyDemoService;
import com.vincent.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderConcurrencyDemoService concurrencyDemoService;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/orders/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void createOrder() throws Exception {
        when(orderService.createOrder(any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"IPHONE17","quantity":1,"requestId":"req-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("created"));
    }

    @Test
    void cancelOrder() throws Exception {
        when(orderService.cancelOrder("ORD-ABC")).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/orders/ORD-ABC/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("cancelled"));
    }

    @Test
    void getOrderStatus() throws Exception {
        when(orderService.getOrderStatus("ORD-ABC")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/orders/status/ORD-ABC"))
                .andExpect(status().isOk());
    }

    @Test
    void getOrder() throws Exception {
        when(orderService.getOrder("ORD-ABC")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/orders/ORD-ABC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value("ORD-ABC"));
    }

    private static OrderResponse sampleResponse() {
        return new OrderResponse(
                "ORD-ABC", "IPHONE17", 1, new BigDecimal("999.00"),
                OrderStatus.CREATED, "req-1", Instant.now(), Instant.now()
        );
    }
}
