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
import com.vincent.orderservice.observability.BusinessEventLog;
import com.vincent.orderservice.kafka.event.InventoryFailedEvent;
import com.vincent.orderservice.kafka.event.InventoryReservedEvent;
import com.vincent.orderservice.kafka.event.OrderCreatedEvent;
import com.vincent.orderservice.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Order orchestration — educational Saga choreography (no 2PC / Seata).
 *
 * <p>Why distributed transactions are hard: inventory and order DBs commit independently.
 * We use events + eventual consistency: order CREATED → Kafka → inventory reserves →
 * inventory-reserved/failed events → order status update.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final Map<String, BigDecimal> DEMO_UNIT_PRICES = Map.of(
            "IPHONE17", new BigDecimal("999.00"),
            "RTX5090", new BigDecimal("1999.00"),
            "PS6", new BigDecimal("599.00")
    );

    private final OrderRepository orderRepository;
    private final OrderIdempotencyStore idempotencyStore;
    private final OrderQueryCache orderQueryCache;
    private final OrderEventPublisher eventPublisher;
    private final OrderMetrics orderMetrics;

    public OrderService(
            OrderRepository orderRepository,
            OrderIdempotencyStore idempotencyStore,
            OrderQueryCache orderQueryCache,
            OrderEventPublisher eventPublisher,
            OrderMetrics orderMetrics
    ) {
        this.orderRepository = orderRepository;
        this.idempotencyStore = idempotencyStore;
        this.orderQueryCache = orderQueryCache;
        this.eventPublisher = eventPublisher;
        this.orderMetrics = orderMetrics;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        return idempotencyStore.findPreviousResult(request.requestId())
                .orElseGet(() -> executeCreateOrder(request));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderNo) {
        return orderQueryCache.get(orderNo)
                .orElseGet(() -> {
                    Order order = loadByOrderNo(orderNo);
                    OrderResponse response = OrderResponse.from(order);
                    orderQueryCache.put(response);
                    return response;
                });
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderStatus(String orderNo) {
        return getOrder(orderNo);
    }

    @Transactional
    public OrderResponse cancelOrder(String orderNo) {
        Order order = loadByOrderNo(orderNo);
        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.FAILED) {
            throw new InvalidOrderStateException(orderNo, order.getStatus(), "cancel");
        }
        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        orderQueryCache.evict(orderNo);
        BusinessEventLog.info(log, "ORDER_CANCELLED", orderNo, saved.getProductCode(), null);
        return OrderResponse.from(saved);
    }

    /**
     * Saga step 2a: inventory reserved — move toward confirmation (eventual consistency).
     */
    @Transactional
    public void onInventoryReserved(InventoryReservedEvent event) {
        Order order = loadByOrderNo(event.orderNo());
        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.FAILED) {
            return;
        }
        order.setStatus(OrderStatus.INVENTORY_RESERVED);
        orderRepository.save(order);
        orderQueryCache.evict(order.getOrderNo());

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        orderQueryCache.evict(order.getOrderNo());
        eventPublisher.publishOrderConfirmed(order.getOrderNo(), event);
        log.info("Order {} confirmed after inventory reserved", order.getOrderNo());
    }

    /**
     * Saga step 2b: inventory failed — mark order FAILED (compensation would release stock in full Saga).
     */
    @Transactional
    public void onInventoryFailed(InventoryFailedEvent event) {
        Order order = loadByOrderNo(event.orderNo());
        if (order.getStatus() == OrderStatus.FAILED || order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);
        orderQueryCache.evict(order.getOrderNo());
        orderMetrics.recordOrderFailed();
        eventPublisher.publishOrderFailed(order.getOrderNo(), event);
        log.warn("Order {} failed: {}", order.getOrderNo(), event.reason());
    }

    private OrderResponse executeCreateOrder(CreateOrderRequest request) {
        if (!idempotencyStore.tryClaim(request.requestId())) {
            return idempotencyStore.findPreviousResult(request.requestId())
                    .orElseThrow(() -> new DuplicateOrderRequestException(request.requestId()));
        }

        try {
            orderRepository.findByRequestId(request.requestId()).ifPresent(existing -> {
                throw new DuplicateOrderRequestException(request.requestId());
            });

            BigDecimal unitPrice = DEMO_UNIT_PRICES.get(request.productCode());
            if (unitPrice == null) {
                throw new IllegalArgumentException("Unknown productCode for demo pricing: " + request.productCode());
            }

            Order order = new Order();
            order.setOrderNo(generateOrderNo());
            order.setProductCode(request.productCode());
            order.setQuantity(request.quantity());
            order.setAmount(unitPrice.multiply(BigDecimal.valueOf(request.quantity())));
            order.setStatus(OrderStatus.CREATED);
            order.setRequestId(request.requestId());

            Order saved = orderRepository.save(order);
            OrderResponse response = OrderResponse.from(saved);

            // PENDING ITEM: future outbox pattern — same transaction as insert
            OrderCreatedEvent createdEvent = OrderCreatedEvent.from(saved);
            eventPublisher.publishOrderCreated(createdEvent);

            idempotencyStore.saveResult(request.requestId(), response);
            orderQueryCache.put(response);
            orderMetrics.recordOrderCreated();
            BusinessEventLog.info(log, "ORDER_CREATED", saved.getOrderNo(), saved.getProductCode(), createdEvent.eventId());
            return response;
        } catch (RuntimeException ex) {
            idempotencyStore.releaseClaim(request.requestId());
            throw ex;
        }
    }

    private Order loadByOrderNo(String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new OrderNotFoundException(orderNo));
    }

    private static String generateOrderNo() {
        return "ORD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
