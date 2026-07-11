package com.vincent.productservice.cache;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.vincent.productservice.config.ProductCacheProperties;
import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.entity.Product;
import com.vincent.productservice.entity.ProductStatus;
import com.vincent.productservice.exception.ProductNotFoundException;
import com.vincent.productservice.mapper.ProductMapper;
import com.vincent.productservice.repository.ProductRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

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
    private final ProductMapper productMapper;
    private final ProductCacheLock cacheLock;
    private final LocalHotProductCache localHotCache;
    private final ProductCacheMetrics cacheMetrics;
    private final Duration detailTtl;
    private final Duration hotTtl;
    private final Duration nullCacheTtl;
    private final Duration lockTtl;
    private final int ttlJitterMaxSeconds;
    private final List<Long> hotProductIds;
    private RedissonClient redissonClient;

    public ProductCacheService(
            StringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            ProductRepository productRepository,
            ProductMapper productMapper,
            ProductCacheLock cacheLock,
            LocalHotProductCache localHotCache,
            ProductCacheMetrics cacheMetrics,
            ProductCacheProperties cacheProperties,
            RedissonClient redissonClient
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.cacheLock = cacheLock;
        this.localHotCache = localHotCache;
        this.cacheMetrics = cacheMetrics;
        this.detailTtl = cacheProperties.detailTtl();
        this.hotTtl = cacheProperties.hotTtl();
        this.nullCacheTtl = cacheProperties.nullCacheTtl();
        this.lockTtl = cacheProperties.lockTtl();
        this.ttlJitterMaxSeconds = cacheProperties.ttlJitterMaxSeconds();
        this.hotProductIds = cacheProperties.hotProductIds();
        this.redissonClient = redissonClient;
    }

    /*
     * 查询单个商品详情的缓存链路：
     *
     * 1. 如果当前商品是配置里的 hot product，先查本 Pod 内存里的 L1 local cache。
     *    这一步最快，不走网络，主要用来吸收热点商品的瞬时高 QPS，减少 Redis 压力。
     *
     * 2. L1 miss 后查 Redis 里的 product:detail:{id}。
     *    Redis 是所有 Pod 共享的 L2 cache；一个 Pod 写入后，其他 Pod 都可以命中，
     *    所以多副本部署时不会因为某个 Pod 本地没缓存就立刻回源 DB。
     *
     * 3. 如果详情缓存 miss，再查 product:notfound:{id} 这种 null cache。
     *    这是为了防止不存在的 id 被反复请求导致一直打 DB，也就是缓存穿透保护。
     *
     * 4. Redis 和 null cache 都 miss 后，说明确实需要回源 DB。
     *    这时先尝试用 Redis SETNX 拿分布式锁，避免多个 Pod 同时发现 miss 后一起查 DB，
     *    也就是降低缓存击穿 / thundering herd 的风险。
     *
     * 5. 如果没拿到锁，说明其他请求正在加载同一个商品。
     *    当前请求短暂等待并轮询 Redis，优先复用锁持有者写回的缓存；
     *    等不到时才使用 fallback 回源 DB，避免无限等待。
     *
     * 6. 如果拿到锁，进入锁内后会再查一次 Redis 和 null cache。
     *    因为拿锁期间可能已经有别的请求写入了缓存；二次检查可以避免重复查 DB。
     *
     * 7. 最终只有确认缓存仍然不存在时，才查询 DB。
     *    查到 ACTIVE 商品后写入 Redis；如果是 hot product，也写入本 Pod 的 L1 local cache。
     *
     * 8. 如果 DB 里不存在或不是 ACTIVE，写入短 TTL 的 null cache。
     *    这样后续同一个无效 id 会快速失败，不会持续打到 DB。
     *
     * 这套逻辑的目标是：优先快路径，Redis 共享缓存兜底，DB 只在必要时访问；
     * 同时用 null cache 防穿透，用分布式锁防击穿，用 local cache 降低热点 Redis 压力。
     */
    public ProductResponse getById(Long id) {
        log.info("com.vincent.productservice.cache.ProductCacheService::getById called");
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
            ProductResponse response = deserialize(cacheKey, cachedJson.get());
            rememberHot(id, response);
            return response;
        }

        String notFoundKey = ProductRedisKeys.notFound(id);
        if (readRaw(notFoundKey).isPresent()) {
            cacheMetrics.recordHit();
            log.debug("Null-cache hit (penetration guard) product id={}", id);
            throw new ProductNotFoundException(id);
        }

        cacheMetrics.recordMiss();
        String lockKey = ProductRedisKeys.detailLock(id);
        String lockToken;
        try {
            lockToken = cacheLock.tryAcquire(lockKey);
        } catch (Exception ex) {
            log.warn("Redis lock unavailable, loading product id={} from DB directly: {}", id, ex.getMessage());
            return loadFromDatabaseAndRememberHotOrWriteNullMarker(id);
        }
        if (lockToken == null) {
            log.debug("Cache miss, waiting for lock holder product id={}", id);
            return waitForPeerLoad(id, () -> loadFromDatabaseAndRememberHotOrWriteNullMarker(id));
        }

        try {
            cachedJson = readRaw(cacheKey);
            if (cachedJson.isPresent()) {
                ProductResponse response = deserialize(cacheKey, cachedJson.get());
                rememberHot(id, response);
                return response;
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
            releaseCacheLock(lockKey, lockToken);
        }
    }

    /*
     * Redisson 版本的商品详情查询链路：
     *
     * 前面的 L1 local cache、Redis detail cache、null cache 和原 getById 保持一致；
     * 差异从“需要回源 DB 并加锁”开始：
     *
     * 1. 使用 RedissonClient.getLock(lockKey) 获取 RLock。
     *    RLock 仍然是基于 Redis key 的分布式锁，但 Redisson 封装了 token、线程持有关系、
     *    unlock 校验等细节，比手写 SET NX + GET/DELETE 更完整。
     *
     * 2. 使用 tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS) 尝试拿锁。
     *    waitTime 这里设为 0，表示不在 Redisson 内部长时间阻塞；
     *    如果锁被其他 Pod 持有，就走 waitForPeerLoad，等待对方回填 Redis。
     *
     * 3. leaseTime 使用 app.cache.product.lock-ttl。
     *    传了 leaseTime 后，锁会在固定时间后自动释放；这个写法更适合和原本 SET NX TTL 对比。
     *    如果希望 Redisson watchdog 自动续期，通常会调用不带 leaseTime 的 lock/tryLock 变体。
     *
     * 4. 如果 Redisson/Redis 本身不可用，不进入 waitForPeerLoad。
     *    因为此时等待 Redis 回填没有意义，直接查 DB，并且写入本 Pod 的 local cache 作为降级。
     *
     * 5. 拿到锁后，和原逻辑一样进行锁内二次检查。
     *    这是为了避免拿锁等待期间已经有其他请求写好了 Redis，减少重复 DB 查询。
     *
     * 6. finally 里使用 lock.isHeldByCurrentThread() 后再 unlock。
     *    Redisson 会避免当前线程误释放不属于自己的锁。
     */
    public ProductResponse getByIdWithRedisson(Long id) {
        if (isHotProduct(id)) {
            Optional<ProductResponse> local = localHotCache.get(id);
            if (local.isPresent()) {
                cacheMetrics.recordHit();
                log.debug("Local hot cache hit product id={} via Redisson path", id);
                return local.get();
            }
        }

        String cacheKey = ProductRedisKeys.detail(id);
        Optional<String> cachedJson = readRaw(cacheKey);
        if (cachedJson.isPresent()) {
            cacheMetrics.recordHit();
            log.debug("Redis cache hit product id={} via Redisson path", id);
            ProductResponse response = deserialize(cacheKey, cachedJson.get());
            rememberHot(id, response);
            return response;
        }

        String notFoundKey = ProductRedisKeys.notFound(id);
        if (readRaw(notFoundKey).isPresent()) {
            cacheMetrics.recordHit();
            log.debug("Null-cache hit (penetration guard) product id={} via Redisson path", id);
            throw new ProductNotFoundException(id);
        }

        cacheMetrics.recordMiss();
        if (redissonClient == null) {
            log.warn("RedissonClient is not available, loading product id={} from DB directly", id);
            return loadFromDatabaseAndRememberHotOrWriteNullMarker(id);
        }

        String lockKey = ProductRedisKeys.detailLock(id);
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked;
        try {
            locked = lock.tryLock(0, lockTtl.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return loadFromDatabaseAndRememberHotOrWriteNullMarker(id);
        } catch (Exception ex) {
            log.warn("Redisson lock unavailable, loading product id={} from DB directly: {}", id, ex.getMessage());
            return loadFromDatabaseAndRememberHotOrWriteNullMarker(id);
        }

        if (!locked) {
            log.debug("Redisson lock busy, waiting for lock holder product id={}", id);
            return waitForPeerLoad(id, () -> loadFromDatabaseAndRememberHotOrWriteNullMarker(id));
        }

        try {
            cachedJson = readRaw(cacheKey);
            if (cachedJson.isPresent()) {
                ProductResponse response = deserialize(cacheKey, cachedJson.get());
                rememberHot(id, response);
                return response;
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
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
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
        return productMapper.toResponse(product);
    }

    private ProductResponse loadFromDatabaseAndRememberHot(Long id) {
        ProductResponse loaded = loadFromDatabase(id);
        rememberHot(id, loaded);
        return loaded;
    }

    private ProductResponse loadFromDatabaseAndRememberHotOrWriteNullMarker(Long id) {
        try {
            return loadFromDatabaseAndRememberHot(id);
        } catch (ProductNotFoundException ex) {
            writeNullMarker(ProductRedisKeys.notFound(id));
            throw ex;
        }
    }

    private void releaseCacheLock(String lockKey, String lockToken) {
        try {
            cacheLock.release(lockKey, lockToken);
        } catch (Exception ex) {
            log.warn("Redis lock release skipped for key={}: {}", lockKey, ex.getMessage());
        }
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
