package com.vincent.productservice.service;

import com.vincent.productservice.dto.ProductMutationRequest;
import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.dto.ProductUpdateRequest;
import com.vincent.productservice.entity.Product;
import com.vincent.productservice.entity.ProductStatus;
import com.vincent.productservice.exception.ProductCodeAlreadyExistsException;
import com.vincent.productservice.exception.ProductCodeNotFoundException;
import com.vincent.productservice.kafka.ProductEventPublisher;
import com.vincent.productservice.kafka.event.ProductCreatedEvent;
import com.vincent.productservice.kafka.event.ProductDeletedEvent;
import com.vincent.productservice.kafka.event.ProductUpdatedEvent;
import com.vincent.productservice.mapper.ProductMapper;
import com.vincent.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductRagSyncCommandServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductEventPublisher eventPublisher = mock(ProductEventPublisher.class);
    private final ProductRagSyncCommandService service = new ProductRagSyncCommandService(
            productRepository,
            productMapper,
            eventPublisher
    );

    @Test
    void createSavesProductPublishesCreatedEventAndReturnsResponse() {
        ProductMutationRequest request = mutationRequest();
        Product saved = product("P1");
        ProductResponse response = response(saved);
        when(productRepository.existsByProductCode("P1")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(saved);
        when(productMapper.toResponse(saved)).thenReturn(response);

        ProductResponse result = service.create(request);

        assertThat(result).isSameAs(response);
        verify(eventPublisher).publishCreated(org.mockito.ArgumentMatchers.argThat(event ->
                event.eventType().equals("PRODUCT_CREATED")
                        && event.productCode().equals("P1")
                        && event.status().equals("ACTIVE")
                        && event.version() > 0
        ));
    }

    @Test
    void createRejectsDuplicateProductCode() {
        when(productRepository.existsByProductCode("P1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(mutationRequest()))
                .isInstanceOf(ProductCodeAlreadyExistsException.class)
                .hasMessageContaining("P1");

        verify(productRepository, never()).save(any(Product.class));
        verify(eventPublisher, never()).publishCreated(any(ProductCreatedEvent.class));
    }

    @Test
    void updateMutatesProductPublishesUpdatedEventAndReturnsResponse() {
        Product existing = product("P1");
        ProductResponse response = response(existing);
        when(productRepository.findByProductCode("P1")).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);
        when(productMapper.toResponse(existing)).thenReturn(response);

        ProductResponse result = service.update("P1", new ProductUpdateRequest(
                "Updated phone", "Updated desc", new BigDecimal("899.00"), 4, ProductStatus.INACTIVE
        ));

        assertThat(result).isSameAs(response);
        assertThat(existing.getName()).isEqualTo("Updated phone");
        assertThat(existing.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        verify(eventPublisher).publishUpdated(org.mockito.ArgumentMatchers.argThat(event ->
                event.eventType().equals("PRODUCT_UPDATED")
                        && event.productCode().equals("P1")
                        && event.status().equals("INACTIVE")
        ));
    }

    @Test
    void updateThrowsWhenProductCodeIsUnknown() {
        when(productRepository.findByProductCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("missing", new ProductUpdateRequest(
                "Phone", "Desc", BigDecimal.TEN, 1, ProductStatus.ACTIVE
        ))).isInstanceOf(ProductCodeNotFoundException.class);

        verify(eventPublisher, never()).publishUpdated(any(ProductUpdatedEvent.class));
    }

    @Test
    void deleteDeletesProductAndPublishesDeletedEvent() {
        Product existing = product("P1");
        when(productRepository.findByProductCode("P1")).thenReturn(Optional.of(existing));

        service.delete("P1");

        verify(productRepository).delete(existing);
        verify(eventPublisher).publishDeleted(org.mockito.ArgumentMatchers.argThat(event ->
                event.eventType().equals("PRODUCT_DELETED")
                        && event.productCode().equals("P1")
                        && event.version() == existing.getUpdatedAt().toEpochMilli()
        ));
    }

    @Test
    void deleteThrowsWhenProductCodeIsUnknown() {
        when(productRepository.findByProductCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("missing"))
                .isInstanceOf(ProductCodeNotFoundException.class);

        verify(eventPublisher, never()).publishDeleted(any(ProductDeletedEvent.class));
    }

    private static ProductMutationRequest mutationRequest() {
        return new ProductMutationRequest("P1", "Camera phone", "Great camera", new BigDecimal("699.00"), 5, ProductStatus.ACTIVE);
    }

    private static Product product(String productCode) {
        Product product = new Product();
        product.setId(1L);
        product.setProductCode(productCode);
        product.setName("Camera phone");
        product.setDescription("Great camera");
        product.setPrice(new BigDecimal("699.00"));
        product.setStock(5);
        product.setStatus(ProductStatus.ACTIVE);
        product.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        product.setUpdatedAt(Instant.parse("2026-01-01T00:00:01Z"));
        return product;
    }

    private static ProductResponse response(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getProductCode(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
