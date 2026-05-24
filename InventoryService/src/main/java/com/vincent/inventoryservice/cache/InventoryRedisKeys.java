package com.vincent.inventoryservice.cache;

/**
 * Namespaced Redis keys for inventory-service.
 */
public final class InventoryRedisKeys {

    public static final String PRODUCT_PREFIX = "inventory:product:";
    public static final String REQUEST_PREFIX = "inventory:request:";
    public static final String LOCK_PREFIX = "inventory:lock:";
    public static final String QUERY_LOCK_SUFFIX = ":query-lock";

    private InventoryRedisKeys() {
    }

    public static String product(String productCode) {
        return PRODUCT_PREFIX + productCode;
    }

    public static String request(String requestId) {
        return REQUEST_PREFIX + requestId;
    }

    public static String lock(String productCode) {
        return LOCK_PREFIX + productCode;
    }

    public static String queryLock(String productCode) {
        return PRODUCT_PREFIX + productCode + QUERY_LOCK_SUFFIX;
    }
}
