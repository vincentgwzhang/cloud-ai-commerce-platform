package com.vincent.orderservice.cache;

/** Namespaced Redis keys for order-service. */
public final class OrderRedisKeys {

    public static final String DETAIL_PREFIX = "order:detail:";
    public static final String REQUEST_PREFIX = "order:request:";

    private OrderRedisKeys() {
    }

    public static String detail(String orderNo) {
        return DETAIL_PREFIX + orderNo;
    }

    public static String request(String requestId) {
        return REQUEST_PREFIX + requestId;
    }
}
