package com.vincent.productservice.service;

import com.vincent.productservice.cache.ProductCacheService;
import com.vincent.productservice.config.ProductCacheProperties;
import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.entity.Product;
import com.vincent.productservice.entity.ProductStatus;
import com.vincent.productservice.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCacheService productCacheService;
    private final ProductCacheProperties cacheProperties;

    public ProductService(
            ProductRepository productRepository,
            ProductCacheService productCacheService,
            ProductCacheProperties cacheProperties
    ) {
        this.productRepository = productRepository;
        this.productCacheService = productCacheService;
        this.cacheProperties = cacheProperties;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listActiveProducts() {
        return productRepository.findByStatusOrderByIdAsc(ProductStatus.ACTIVE).stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse getProduct(Long id) {
        return productCacheService.getById(id);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listHotProducts() {
        List<Long> hotIds = cacheProperties.hotProductIds();
        if (hotIds == null || hotIds.isEmpty()) {
            return List.of();
        }
        return productCacheService.getHotList().orElseGet(() -> {
            List<ProductResponse> loaded = productRepository.findByIdInOrderByIdAsc(hotIds).stream()
                    .filter(product -> product.getStatus() == ProductStatus.ACTIVE)
                    .map(ProductResponse::from)
                    .toList();
            productCacheService.putHotList(loaded);
            return loaded;
        });
    }

    public void warmCache(Product product) {
        productCacheService.put(ProductResponse.from(product));
    }
}
