package com.vincent.productservice.controller;

import com.vincent.productservice.dto.ApiResponse;
import com.vincent.productservice.dto.ProductMutationRequest;
import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.dto.ProductUpdateRequest;
import com.vincent.productservice.service.ProductRagSyncCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/product-sync/products")
@PreAuthorize("hasRole('ADMIN')")
public class ProductRagSyncController {

    private final ProductRagSyncCommandService commandService;

    public ProductRagSyncController(ProductRagSyncCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a product and publish product-created for AI vector sync", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductMutationRequest request) {
        return ApiResponse.ok(commandService.create(request));
    }

    @PutMapping("/{productCode}")
    @Operation(summary = "Update a product and publish product-updated for AI vector sync", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<ProductResponse> update(
            @PathVariable String productCode,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        return ApiResponse.ok(commandService.update(productCode, request));
    }

    @DeleteMapping("/{productCode}")
    @Operation(summary = "Delete a product and publish product-deleted for AI vector sync", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<Void> delete(@PathVariable String productCode) {
        commandService.delete(productCode);
        return ApiResponse.ok(null);
    }
}
