package com.vincent.productservice.controller;

import com.vincent.productservice.dto.ProductMutationRequest;
import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.dto.ProductUpdateRequest;
import com.vincent.productservice.entity.ProductStatus;
import com.vincent.productservice.service.ProductRagSyncCommandService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductRagSyncControllerTest {

    private final ProductRagSyncCommandService commandService = mock(ProductRagSyncCommandService.class);
    private final ProductRagSyncController controller = new ProductRagSyncController(commandService);

    @Test
    void createDelegatesToCommandService() {
        ProductMutationRequest request = mutationRequest();
        ProductResponse response = response();
        when(commandService.create(request)).thenReturn(response);

        assertThat(controller.create(request).data()).isSameAs(response);
    }

    @Test
    void updateDelegatesToCommandService() {
        ProductUpdateRequest request = new ProductUpdateRequest("Updated", "Desc", BigDecimal.TEN, 2, ProductStatus.ACTIVE);
        ProductResponse response = response();
        when(commandService.update("P1", request)).thenReturn(response);

        assertThat(controller.update("P1", request).data()).isSameAs(response);
    }

    @Test
    void deleteDelegatesToCommandService() {
        assertThat(controller.delete("P1").success()).isTrue();
        verify(commandService).delete("P1");
    }

    private static ProductMutationRequest mutationRequest() {
        return new ProductMutationRequest("P1", "Phone", "Desc", BigDecimal.TEN, 2, ProductStatus.ACTIVE);
    }

    private static ProductResponse response() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new ProductResponse(1L, "P1", "Phone", "Desc", BigDecimal.TEN, 2, ProductStatus.ACTIVE, now, now);
    }
}
