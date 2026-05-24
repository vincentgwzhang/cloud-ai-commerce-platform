package com.vincent.productservice.bootstrap;

import com.vincent.productservice.config.ProductCacheProperties;
import com.vincent.productservice.entity.Product;
import com.vincent.productservice.repository.ProductRepository;
import com.vincent.productservice.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Preloads configured hot product IDs into Redis at startup (cache warming).
 */
@Component
@Profile("!test")
public class HotProductPreloader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HotProductPreloader.class);

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final ProductCacheProperties cacheProperties;

    public HotProductPreloader(
            ProductRepository productRepository,
            ProductService productService,
            ProductCacheProperties cacheProperties
    ) {
        this.productRepository = productRepository;
        this.productService = productService;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Long> hotIds = cacheProperties.hotProductIds();
        if (hotIds == null || hotIds.isEmpty()) {
            return;
        }
        List<Product> products = productRepository.findByIdInOrderByIdAsc(hotIds);
        products.forEach(productService::warmCache);
        log.info("Preloaded {} hot products into Redis", products.size());
    }
}
