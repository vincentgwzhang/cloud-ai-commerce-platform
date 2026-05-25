package com.vincent.productservice.service;

import com.vincent.productservice.cache.ProductCacheService;
import com.vincent.productservice.config.ProductCacheProperties;
import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.entity.Product;
import com.vincent.productservice.entity.ProductStatus;
import com.vincent.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCacheService productCacheService;

    @Mock
    private ProductCacheProperties cacheProperties;

    @InjectMocks
    private ProductService productService;

    @Test
    void getProductDelegatesToCache() {
        ProductResponse cached = new ProductResponse(
                1L, "CODE", "Name", "d", BigDecimal.TEN, 1, ProductStatus.ACTIVE,
                Instant.now(), Instant.now()
        );
        when(productCacheService.getById(1L)).thenReturn(cached);

        assertThat(productService.getProduct(1L)).isEqualTo(cached);
    }

    @Test
    void listHotProductsUsesConfiguredIds() {
        Product product = new Product();
        product.setId(1L);
        product.setProductCode("IPHONE-17");
        product.setName("iPhone 17");
        product.setPrice(BigDecimal.TEN);
        product.setStock(1);
        product.setStatus(ProductStatus.ACTIVE);
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());

        when(cacheProperties.hotProductIds()).thenReturn(List.of(1L));
        when(productRepository.findByIdInOrderByIdAsc(List.of(1L))).thenReturn(List.of(product));

        List<ProductResponse> hot = productService.listHotProducts();

        assertThat(hot).hasSize(1);
        assertThat(hot.getFirst().name()).isEqualTo("iPhone 17");
    }

    @Test
    void warmCacheDelegatesToCacheService() {
        Product product = new Product();
        product.setId(2L);
        product.setProductCode("RTX-5090");
        product.setName("GPU");
        product.setDescription("d");
        product.setPrice(BigDecimal.ONE);
        product.setStock(5);
        product.setStatus(ProductStatus.ACTIVE);
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());

        productService.warmCache(product);

        verify(productCacheService).put(org.mockito.ArgumentMatchers.any(ProductResponse.class));
    }
}
