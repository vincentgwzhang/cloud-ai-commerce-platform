package com.vincent.productservice.cache;

/**
 * Namespaced Redis keys — prevents collision with other services sharing the same Redis instance.
 */
public final class ProductRedisKeys {

    public static final String DETAIL_PREFIX = "product:detail:";
    public static final String NOT_FOUND_PREFIX = "product:notfound:";
    public static final String HOT_LIST = "product:hot:list";

    private ProductRedisKeys() {
    }

    public static String detail(Long id) {
        return DETAIL_PREFIX + id;
    }

    public static String notFound(Long id) {
        return NOT_FOUND_PREFIX + id;
    }

    public static String detailLock(Long id) {
        return DETAIL_PREFIX + id + ":lock";
    }
}
