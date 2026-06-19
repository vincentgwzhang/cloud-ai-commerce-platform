package com.vincent.orderservice.service;

import com.vincent.orderservice.cache.OrderIdempotencyStore;
import com.vincent.orderservice.cache.OrderQueryCache;
import com.vincent.orderservice.dto.CreateOrderRequest;
import com.vincent.orderservice.dto.OrderResponse;
import com.vincent.orderservice.entity.Order;
import com.vincent.orderservice.entity.OrderStatus;
import com.vincent.orderservice.exception.DuplicateOrderRequestException;
import com.vincent.orderservice.exception.InvalidOrderStateException;
import com.vincent.orderservice.exception.OrderNotFoundException;
import com.vincent.orderservice.kafka.OrderEventPublisher;
import com.vincent.orderservice.kafka.event.InventoryFailedEvent;
import com.vincent.orderservice.kafka.event.InventoryReservedEvent;
import com.vincent.orderservice.kafka.event.OrderCreatedEvent;
import com.vincent.orderservice.mapper.OrderMapper;
import com.vincent.orderservice.repository.OrderRepository;
import org.mapstruct.factory.Mappers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderIdempotencyStore idempotencyStore;
    @Mock
    private OrderQueryCache orderQueryCache;
    @Mock
    private OrderEventPublisher eventPublisher;
    @Mock
    private OrderMetrics orderMetrics;

    @Spy
    private OrderMapper orderMapper = Mappers.getMapper(OrderMapper.class);

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrderReturnsCachedIdempotentResult() {
        OrderResponse cached = sampleResponse("ORD-1");
        when(idempotencyStore.findPreviousResult("req-1")).thenReturn(Optional.of(cached));

        assertThat(orderService.createOrder(new CreateOrderRequest("IPHONE17", 1, "req-1"), "vincent")).isEqualTo(cached);
        verify(eventPublisher, never()).publishOrderCreated(any());
    }

    @Test
    void createOrderSucceeds() {
        when(idempotencyStore.findPreviousResult("req-2")).thenReturn(Optional.empty());
        when(idempotencyStore.tryClaim("req-2")).thenReturn(true);
        when(orderRepository.findByRequestId("req-2")).thenReturn(Optional.empty());
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(1L);
            order.setCreatedAt(Instant.now());
            order.setUpdatedAt(Instant.now());
            return order;
        });

        OrderResponse response = orderService.createOrder(new CreateOrderRequest("IPHONE17", 2, "req-2"), "vincent");

        assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.amount()).isEqualByComparingTo("1998.00");
        ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(eventPublisher).publishOrderCreated(eventCaptor.capture());
        assertThat(eventCaptor.getValue().username()).isEqualTo("vincent");
        verify(orderMetrics).recordOrderCreated();
    }

    @Test
    void getOrderThrowsWhenMissing() {
        when(orderQueryCache.get("ORD-X")).thenReturn(Optional.empty());
        when(orderRepository.findByOrderNo("ORD-X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder("ORD-X")).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void onInventoryFailedMarksOrderFailed() {
        Order order = sampleOrder("ORD-FAIL");
        when(orderRepository.findByOrderNo("ORD-FAIL")).thenReturn(Optional.of(order));

        orderService.onInventoryFailed(new InventoryFailedEvent("ORD-FAIL", "IPHONE17", "no stock"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(orderMetrics).recordOrderFailed();
    }

    @Test
    void cancelOrderSucceeds() {
        Order order = sampleOrder("ORD-C");
        when(orderRepository.findByOrderNo("ORD-C")).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder("ORD-C");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderQueryCache).evict("ORD-C");
    }

    @Test
    void cancelOrderFailsWhenConfirmed() {
        Order order = sampleOrder("ORD-X");
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByOrderNo("ORD-X")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder("ORD-X"))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void createOrderFailsForUnknownProduct() {
        when(idempotencyStore.findPreviousResult("req-bad")).thenReturn(Optional.empty());
        when(idempotencyStore.tryClaim("req-bad")).thenReturn(true);
        when(orderRepository.findByRequestId("req-bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest("UNKNOWN", 1, "req-bad"), "vincent"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(idempotencyStore).releaseClaim("req-bad");
    }

    @Test
    void createOrderThrowsWhenDuplicateInProgress() {
        when(idempotencyStore.findPreviousResult("req-dup"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(idempotencyStore.tryClaim("req-dup")).thenReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(new CreateOrderRequest("IPHONE17", 1, "req-dup"), "vincent"))
                .isInstanceOf(DuplicateOrderRequestException.class);
    }

    @Test
    void onInventoryReservedSkipsWhenAlreadyConfirmed() {
        Order order = sampleOrder("ORD-SKIP");
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByOrderNo("ORD-SKIP")).thenReturn(Optional.of(order));

        orderService.onInventoryReserved(new InventoryReservedEvent("ORD-SKIP", "IPHONE17", 1));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void onInventoryReservedConfirmsOrder() {
        Order order = sampleOrder("ORD-OK");
        when(orderRepository.findByOrderNo("ORD-OK")).thenReturn(Optional.of(order));

        orderService.onInventoryReserved(new InventoryReservedEvent("ORD-OK", "IPHONE17", 1));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    private static OrderResponse sampleResponse(String orderNo) {
        return new OrderResponse(
                orderNo, "IPHONE17", 1, new BigDecimal("999.00"),
                OrderStatus.CREATED, "req-1", Instant.now(), Instant.now()
        );
    }

    private static Order sampleOrder(String orderNo) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo(orderNo);
        order.setProductCode("IPHONE17");
        order.setQuantity(1);
        order.setAmount(new BigDecimal("999.00"));
        order.setStatus(OrderStatus.CREATED);
        order.setRequestId("req-x");
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        return order;
    }
}
