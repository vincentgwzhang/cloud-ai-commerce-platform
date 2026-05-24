package com.vincent.productservice.cache;

import com.vincent.productservice.config.ProductCacheProperties;
import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.entity.Product;
import com.vincent.productservice.entity.ProductStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalHotProductCacheTest {

    @Test
    void storesAndReturnsWithinTtl() {
        ProductCacheProperties properties = new ProductCacheProperties(
                Duration.ofMinutes(10),
                Duration.ofMinutes(30),
                Duration.ofMinutes(2),
                Duration.ofSeconds(30),
                Duration.ofHours(1),
                0,
                List.of(1L)
        );
        LocalHotProductCache cache = new LocalHotProductCache(properties);
        ProductResponse response = ProductResponse.from(sample(1L));
        cache.put(1L, response);
        assertThat(cache.get(1L)).contains(response);
    }

    private static Product sample(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setProductCode("C");
        product.setName("N");
        product.setPrice(BigDecimal.ONE);
        product.setStock(1);
        product.setStatus(ProductStatus.ACTIVE);
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        return product;
    }
}
