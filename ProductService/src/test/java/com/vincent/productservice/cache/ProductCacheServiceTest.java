package com.vincent.productservice.cache;

import tools.jackson.databind.json.JsonMapper;
import com.vincent.productservice.config.ProductCacheProperties;
import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.entity.Product;
import com.vincent.productservice.entity.ProductStatus;
import com.vincent.productservice.exception.ProductNotFoundException;
import com.vincent.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCacheLock cacheLock;

    private ProductCacheService productCacheService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ProductCacheProperties properties = new ProductCacheProperties(
                Duration.ofMinutes(10),
                Duration.ofSeconds(30),
                "product:",
                List.of(1L, 2L, 3L)
        );
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        productCacheService = new ProductCacheService(
                redisTemplate,
                mapper,
                productRepository,
                cacheLock,
                properties
        );
    }

    @Test
    void getByIdReturnsCachedValueOnHit() throws Exception {
        ProductResponse response = sampleResponse();
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        when(valueOperations.get("product:1")).thenReturn(mapper.writeValueAsString(response));

        ProductResponse result = productCacheService.getById(1L);

        assertThat(result.name()).isEqualTo("Product 1");
        verify(productRepository, never()).findById(any());
    }

    @Test
    void getByIdLoadsDatabaseOnMissWithLock() throws Exception {
        when(valueOperations.get("product:99")).thenReturn(null);
        when(cacheLock.tryAcquire("product:99:lock")).thenReturn("token-1");
        when(productRepository.findById(99L)).thenReturn(Optional.of(sampleEntity(99L)));

        ProductResponse result = productCacheService.getById(99L);

        assertThat(result.id()).isEqualTo(99L);
        verify(valueOperations).set(eq("product:99"), anyString(), eq(Duration.ofMinutes(10)));
        verify(cacheLock).release("product:99:lock", "token-1");
    }

    @Test
    void getByIdThrowsWhenProductMissing() {
        when(valueOperations.get("product:404")).thenReturn(null);
        when(cacheLock.tryAcquire("product:404:lock")).thenReturn("token-1");
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productCacheService.getById(404L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getByIdReturnsPeerLoadedCacheWhenLockNotAcquired() throws Exception {
        ProductResponse response = sampleResponse();
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        String json = mapper.writeValueAsString(response);
        when(valueOperations.get("product:1")).thenReturn(null, null, json);
        when(cacheLock.tryAcquire("product:1:lock")).thenReturn(null);

        ProductResponse result = productCacheService.getById(1L);

        assertThat(result.name()).isEqualTo("Product 1");
        verify(productRepository, never()).findById(any());
    }

    @Test
    void getByIdUsesDoubleCheckedCacheAfterLock() throws Exception {
        ProductResponse response = ProductResponse.from(sampleEntity(2L));
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        when(valueOperations.get("product:2"))
                .thenReturn(null)
                .thenReturn(mapper.writeValueAsString(response));
        when(cacheLock.tryAcquire("product:2:lock")).thenReturn("token-2");

        ProductResponse result = productCacheService.getById(2L);

        assertThat(result.id()).isEqualTo(2L);
        verify(productRepository, never()).findById(any());
        verify(cacheLock).release("product:2:lock", "token-2");
    }

    @Test
    void getByIdEvictsInvalidCachePayload() throws Exception {
        when(valueOperations.get("product:3")).thenReturn("{invalid-json");
        when(cacheLock.tryAcquire("product:3:lock")).thenReturn("token-3");
        when(productRepository.findById(3L)).thenReturn(Optional.of(sampleEntity(3L)));

        ProductResponse result = productCacheService.getById(3L);

        assertThat(result.id()).isEqualTo(3L);
        verify(redisTemplate, org.mockito.Mockito.times(2)).delete("product:3");
        verify(valueOperations).set(eq("product:3"), anyString(), eq(Duration.ofMinutes(10)));
    }

    @Test
    void putWritesToCache() throws Exception {
        ProductResponse response = sampleResponse();

        productCacheService.put(response);

        verify(valueOperations).set(eq("product:1"), anyString(), eq(Duration.ofMinutes(10)));
    }

    private static Product sampleEntity(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setProductCode("CODE-" + id);
        product.setName("Product " + id);
        product.setDescription("desc");
        product.setPrice(BigDecimal.TEN);
        product.setStock(5);
        product.setStatus(ProductStatus.ACTIVE);
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        return product;
    }

    private static ProductResponse sampleResponse() {
        return ProductResponse.from(sampleEntity(1L));
    }
}
