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
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cache-aside for product detail with SETNX lock on cache miss (anti cache breakdown).
 */
@Service
public class ProductCacheService {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final ProductRepository productRepository;
    private final ProductCacheLock cacheLock;
    private final String keyPrefix;
    private final Duration ttl;

    public ProductCacheService(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            ProductRepository productRepository,
            ProductCacheLock cacheLock,
            ProductCacheProperties cacheProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.productRepository = productRepository;
        this.cacheLock = cacheLock;
        this.keyPrefix = cacheProperties.keyPrefix();
        this.ttl = cacheProperties.ttl();
    }

    public ProductResponse getById(Long id) {
        String cacheKey = cacheKey(id);

        Optional<ProductResponse> cached = readCache(cacheKey);
        if (cached.isPresent()) {
            log.debug("Cache hit for product id={}", id);
            return cached.get();
        }

        String lockKey = cacheKey + ":lock";
        String lockToken = cacheLock.tryAcquire(lockKey);
        if (lockToken == null) {
            log.debug("Cache miss, waiting for lock holder product id={}", id);
            return waitForPeerLoad(cacheKey, () -> loadFromDatabase(id));
        }

        try {
            cached = readCache(cacheKey);
            if (cached.isPresent()) {
                return cached.get();
            }
            ProductResponse loaded = loadFromDatabase(id);
            writeCache(cacheKey, loaded);
            return loaded;
        } finally {
            cacheLock.release(lockKey, lockToken);
        }
    }

    public void put(ProductResponse product) {
        writeCache(cacheKey(product.id()), product);
    }

    private ProductResponse waitForPeerLoad(String cacheKey, Supplier<ProductResponse> fallback) {
        for (int attempt = 0; attempt < 5; attempt++) {
            Optional<ProductResponse> cached = readCache(cacheKey);
            if (cached.isPresent()) {
                return cached.get();
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

    private Optional<ProductResponse> readCache(String cacheKey) {
        String json = redisTemplate.opsForValue().get(cacheKey);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonMapper.readValue(json, ProductResponse.class));
        } catch (JacksonException ex) {
            log.warn("Invalid cache payload for key={}, evicting", cacheKey);
            redisTemplate.delete(cacheKey);
            return Optional.empty();
        }
    }

    private void writeCache(String cacheKey, ProductResponse product) {
        try {
            redisTemplate.opsForValue().set(cacheKey, jsonMapper.writeValueAsString(product), ttl);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize product cache", ex);
        }
    }

    private String cacheKey(Long id) {
        return keyPrefix + id;
    }
}
