package com.vincent.productservice.cache;

import com.vincent.productservice.config.ProductCacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 用 Redis 实现的一个简化版分布式锁，用来降低热点商品缓存击穿。
 *
 * <p>这个类是教学 POC，不是完整的 Redlock 实现。它主要演示三个核心点：
 *
 * <p>1. 加锁：用 Redis 的 SET NX 语义，也就是 {@code setIfAbsent}。
 * 只有当 lockKey 不存在时，当前请求才能写入成功；如果 lockKey 已经存在，
 * 说明别的请求已经在回源 DB 并准备重建缓存，当前请求就不应该一起打到 DB。
 *
 * <p>2. 锁 value：不是随便写一个固定值，而是写入本次请求生成的随机 token。
 * 这个 token 可以理解为“锁的所有权凭证”。谁拿到了锁，谁才知道自己的 token；
 * 后面释放锁时必须带着同一个 token 来证明“这把锁是我加的”。
 *
 * <p>3. 过期时间：加锁时同时设置 lockTtl。
 * 如果拿到锁的应用实例宕机、线程被杀死、或者业务代码抛异常没来得及释放锁，
 * Redis 也会在 TTL 到期后自动删除 lockKey，避免锁永远留在 Redis 里形成死锁。
 */
@Component
public class ProductCacheLock {

    private final StringRedisTemplate redisTemplate;
    private final Duration lockTtl;

    public ProductCacheLock(StringRedisTemplate redisTemplate, ProductCacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.lockTtl = cacheProperties.lockTtl();
    }

    /*
     * 尝试获取锁。
     *
     * lockKey:
     *   锁对应的 Redis key，例如 product:detail:99:lock。
     *   同一个商品详情缓存 miss 时，所有请求都会竞争同一个 lockKey。
     *
     * token:
     *   每次尝试加锁都会生成一个随机 UUID。
     *   它不是为了“判断锁是否存在”，而是为了“判断锁是不是当前请求持有的”。
     *
     * setIfAbsent(lockKey, token, lockTtl):
     *   等价于 Redis 的 SET key value NX EX/PX。
     *   NX 表示 key 不存在才写入，也就是抢锁；
     *   lockTtl 表示写入时同时带过期时间，避免死锁。
     *
     * 返回值:
     *   - 返回 token：说明当前请求抢锁成功，调用方可以安全地回源 DB 重建缓存。
     *   - 返回 null：说明锁已经被别人拿走，调用方应该等待一会儿再读缓存，
     *     或者走自己的降级逻辑，而不是一起打到 DB。
     */
    public String tryAcquire(String lockKey) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, lockTtl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    /*
     * 释放锁。
     *
     * 为什么不能直接 redisTemplate.delete(lockKey)?
     *
     * 假设请求 A 拿到了锁，token=A。
     * A 因为 DB 很慢或线程卡顿，执行时间超过了 lockTtl，Redis 自动删除了这把锁。
     * 这时请求 B 进来，又成功拿到了同一个 lockKey，token=B。
     * 如果 A 终于执行完，然后不检查 token 就直接 delete(lockKey)，
     * 它会把 B 刚刚拿到的锁删掉。
     *
     * 所以释放前必须先读当前 Redis 里的 value：
     *   - 如果 current == token，说明这把锁仍然是当前请求持有的，可以删除。
     *   - 如果 current != token，说明锁已经过期后被别人重新拿走，当前请求不能删。
     *
     * 注意：
     *   这里的 get + delete 不是原子操作，严格生产级实现通常会用 Lua 脚本保证
     *   “比较 token 并删除”是一个原子动作。这个项目里保留这种写法，是为了让
     *   SETNX 锁、token 所有权、防误删这些概念更容易看懂。
     */
    public void release(String lockKey, String token) {
        String current = redisTemplate.opsForValue().get(lockKey);
        if (token != null && token.equals(current)) {
            redisTemplate.delete(lockKey);
        }
    }
}
