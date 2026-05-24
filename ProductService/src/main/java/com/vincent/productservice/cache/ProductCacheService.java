package com.vincent.productservice.cache;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import com.vincent.productservice.config.ProductCacheProperties;
import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.entity.Product;
import com.vincent.productservice.entity.ProductStatus;
import com.vincent.productservice.exception.ProductNotFoundException;
import com.vincent.productservice.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cache-aside for product detail with SETNX lock on miss (anti cache breakdown / thundering herd).
 *
 * <p>Null-result short TTL blocks cache penetration on invalid IDs.
 */
@Service
public class ProductCacheService {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheService.class);
    private static final String NULL_MARKER = "1";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final ProductRepository productRepository;
    private final ProductCacheLock cacheLock;
    private final LocalHotProductCache localHotCache;
    private final ProductCacheMetrics cacheMetrics;
    private final Duration detailTtl;
    private final Duration hotTtl;
    private final Duration nullCacheTtl;
    private final int ttlJitterMaxSeconds;
    private final List<Long> hotProductIds;

    public ProductCacheService(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            ProductRepository productRepository,
            ProductCacheLock cacheLock,
            LocalHotProductCache localHotCache,
            ProductCacheMetrics cacheMetrics,
            ProductCacheProperties cacheProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.productRepository = productRepository;
        this.cacheLock = cacheLock;
        this.localHotCache = localHotCache;
        this.cacheMetrics = cacheMetrics;
        this.detailTtl = cacheProperties.detailTtl();
        this.hotTtl = cacheProperties.hotTtl();
        this.nullCacheTtl = cacheProperties.nullCacheTtl();
        this.ttlJitterMaxSeconds = cacheProperties.ttlJitterMaxSeconds();
        this.hotProductIds = cacheProperties.hotProductIds();
    }

    public ProductResponse getById(Long id) {
        if (isHotProduct(id)) {
            Optional<ProductResponse> local = localHotCache.get(id);
            if (local.isPresent()) {
                cacheMetrics.recordHit();
                log.debug("Local hot cache hit product id={}", id);
                return local.get();
            }
        }

        String cacheKey = ProductRedisKeys.detail(id);
        Optional<String> cachedJson = readRaw(cacheKey);
        if (cachedJson.isPresent()) {
            cacheMetrics.recordHit();
            log.debug("Redis cache hit product id={}", id);
            return deserialize(cacheKey, cachedJson.get());
        }

        String notFoundKey = ProductRedisKeys.notFound(id);
        if (readRaw(notFoundKey).isPresent()) {
            cacheMetrics.recordHit();
            log.debug("Null-cache hit (penetration guard) product id={}", id);
            throw new ProductNotFoundException(id);
        }

        cacheMetrics.recordMiss();
        String lockKey = ProductRedisKeys.detailLock(id);
        String lockToken = cacheLock.tryAcquire(lockKey);
        if (lockToken == null) {
            log.debug("Cache miss, waiting for lock holder product id={}", id);
            return waitForPeerLoad(id, () -> loadFromDatabase(id));
        }

        try {
            cachedJson = readRaw(cacheKey);
            if (cachedJson.isPresent()) {
                return deserialize(cacheKey, cachedJson.get());
            }
            if (readRaw(notFoundKey).isPresent()) {
                throw new ProductNotFoundException(id);
            }
            ProductResponse loaded = loadFromDatabase(id);
            writeDetail(cacheKey, loaded);
            rememberHot(id, loaded);
            return loaded;
        } catch (ProductNotFoundException ex) {
            writeNullMarker(notFoundKey);
            throw ex;
        } finally {
            cacheLock.release(lockKey, lockToken);
        }
    }

    public void put(ProductResponse product) {
        writeDetail(ProductRedisKeys.detail(product.id()), product);
        rememberHot(product.id(), product);
    }

    public Optional<List<ProductResponse>> getHotList() {
        return readRaw(ProductRedisKeys.HOT_LIST).map(this::deserializeHotList);
    }

    public void putHotList(List<ProductResponse> products) {
        Duration ttl = RedisTtlJitter.apply(hotTtl, ttlJitterMaxSeconds);
        RedisSafeExecutor.run(() -> {
            try {
                redisTemplate.opsForValue().set(
                        ProductRedisKeys.HOT_LIST,
                        jsonMapper.writeValueAsString(products),
                        ttl
                );
            } catch (JacksonException ex) {
                throw new IllegalStateException("Failed to serialize hot list", ex);
            }
        });
    }

    private ProductResponse waitForPeerLoad(Long id, Supplier<ProductResponse> fallback) {
        String cacheKey = ProductRedisKeys.detail(id);
        String notFoundKey = ProductRedisKeys.notFound(id);
        for (int attempt = 0; attempt < 5; attempt++) {
            Optional<String> cached = readRaw(cacheKey);
            if (cached.isPresent()) {
                return deserialize(cacheKey, cached.get());
            }
            if (readRaw(notFoundKey).isPresent()) {
                throw new ProductNotFoundException(id);
            }
            try {
                Thread.sleep(50L * (attempt + 1));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return fallback.get();
    }

    private ProductResponse loadFromDatabase(Long id) {
        Product product = productRepository.findById(id)
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ProductResponse.from(product);
    }

    private Optional<String> readRaw(String key) {
        return RedisSafeExecutor.optional(() -> redisTemplate.opsForValue().get(key));
    }

    private ProductResponse deserialize(String cacheKey, String json) {
        try {
            return jsonMapper.readValue(json, ProductResponse.class);
        } catch (JacksonException ex) {
            log.warn("Invalid cache payload for key={}, evicting", cacheKey);
            RedisSafeExecutor.run(() -> redisTemplate.delete(cacheKey));
            throw new IllegalStateException("Invalid cache payload", ex);
        }
    }

    private List<ProductResponse> deserializeHotList(String json) {
        try {
            return jsonMapper.readValue(
                    json,
                    jsonMapper.getTypeFactory().constructCollectionType(List.class, ProductResponse.class)
            );
        } catch (JacksonException ex) {
            throw new IllegalStateException("Invalid hot list cache", ex);
        }
    }

    private void writeDetail(String cacheKey, ProductResponse product) {
        Duration ttl = RedisTtlJitter.apply(resolveTtl(product.id()), ttlJitterMaxSeconds);
        RedisSafeExecutor.run(() -> {
            try {
                redisTemplate.opsForValue().set(cacheKey, jsonMapper.writeValueAsString(product), ttl);
            } catch (JacksonException ex) {
                throw new IllegalStateException("Failed to serialize product cache", ex);
            }
        });
    }

    private void writeNullMarker(String notFoundKey) {
        Duration ttl = RedisTtlJitter.apply(nullCacheTtl, ttlJitterMaxSeconds);
        RedisSafeExecutor.run(() -> redisTemplate.opsForValue().set(notFoundKey, NULL_MARKER, ttl));
    }

    private Duration resolveTtl(Long id) {
        return isHotProduct(id) ? hotTtl : detailTtl;
    }

    private boolean isHotProduct(Long id) {
        return hotProductIds != null && hotProductIds.contains(id);
    }

    private void rememberHot(Long id, ProductResponse response) {
        if (isHotProduct(id)) {
            localHotCache.put(id, response);
        }
    }
}
