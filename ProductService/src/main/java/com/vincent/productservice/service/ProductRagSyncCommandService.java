package com.vincent.productservice.service;

import com.vincent.productservice.dto.ProductMutationRequest;
import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.dto.ProductUpdateRequest;
import com.vincent.productservice.entity.Product;
import com.vincent.productservice.exception.ProductCodeAlreadyExistsException;
import com.vincent.productservice.exception.ProductCodeNotFoundException;
import com.vincent.productservice.kafka.ProductEventPublisher;
import com.vincent.productservice.kafka.event.ProductCreatedEvent;
import com.vincent.productservice.kafka.event.ProductDeletedEvent;
import com.vincent.productservice.kafka.event.ProductUpdatedEvent;
import com.vincent.productservice.mapper.ProductMapper;
import com.vincent.productservice.observability.MdcSupport;
import com.vincent.productservice.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProductRagSyncCommandService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductEventPublisher eventPublisher;

    public ProductRagSyncCommandService(
            ProductRepository productRepository,
            ProductMapper productMapper,
            ProductEventPublisher eventPublisher
    ) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProductResponse create(ProductMutationRequest request) {
        if (productRepository.existsByProductCode(request.productCode())) {
            throw new ProductCodeAlreadyExistsException(request.productCode());
        }

        Instant now = Instant.now();
        Product product = new Product();
        product.setProductCode(request.productCode());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setStatus(request.status());
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        Product saved = productRepository.save(product);
        ProductCreatedEvent event = createdEvent(saved, now);
        afterCommit(() -> eventPublisher.publishCreated(event));
        return productMapper.toResponse(saved);
    }

    @Transactional
    public ProductResponse update(String productCode, ProductUpdateRequest request) {
        Product product = productRepository.findByProductCode(productCode)
                .orElseThrow(() -> new ProductCodeNotFoundException(productCode));

        Instant now = Instant.now();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setStatus(request.status());
        product.setUpdatedAt(now);

        Product saved = productRepository.save(product);
        ProductUpdatedEvent event = updatedEvent(saved, now);
        afterCommit(() -> eventPublisher.publishUpdated(event));
        return productMapper.toResponse(saved);
    }

    @Transactional
    public void delete(String productCode) {
        Product product = productRepository.findByProductCode(productCode)
                .orElseThrow(() -> new ProductCodeNotFoundException(productCode));

        Instant occurredAt = Instant.now();
        long version = version(product.getUpdatedAt());
        productRepository.delete(product);
        ProductDeletedEvent event = new ProductDeletedEvent(
                UUID.randomUUID().toString(),
                "PRODUCT_DELETED",
                productCode,
                version,
                occurredAt,
                MdcSupport.requestId().orElse(null),
                MdcSupport.traceId().orElse(null)
        );
        afterCommit(() -> eventPublisher.publishDeleted(event));
    }

    private static ProductCreatedEvent createdEvent(Product product, Instant occurredAt) {
        return new ProductCreatedEvent(
                UUID.randomUUID().toString(),
                "PRODUCT_CREATED",
                product.getProductCode(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus().name(),
                version(product.getUpdatedAt()),
                occurredAt,
                MdcSupport.requestId().orElse(null),
                MdcSupport.traceId().orElse(null)
        );
    }

    private static ProductUpdatedEvent updatedEvent(Product product, Instant occurredAt) {
        return new ProductUpdatedEvent(
                UUID.randomUUID().toString(),
                "PRODUCT_UPDATED",
                product.getProductCode(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus().name(),
                version(product.getUpdatedAt()),
                occurredAt,
                MdcSupport.requestId().orElse(null),
                MdcSupport.traceId().orElse(null)
        );
    }

    private static long version(Instant updatedAt) {
        return updatedAt == null ? 0L : updatedAt.toEpochMilli();
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
