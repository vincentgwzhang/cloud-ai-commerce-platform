package com.vincent.productservice.controller;

import com.vincent.productservice.dto.ApiResponse;
import com.vincent.productservice.dto.HealthResponse;
import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/health")
    @Operation(summary = "Service health check")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.ok(new HealthResponse("UP"));
    }

    @GetMapping
    @Operation(summary = "List active products", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<List<ProductResponse>> listProducts() {
        return ApiResponse.ok(productService.listActiveProducts());
    }

    @GetMapping("/hot")
    @Operation(summary = "List hot products (preloaded in Redis)", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<List<ProductResponse>> listHotProducts() {
        return ApiResponse.ok(productService.listHotProducts());
    }

    @GetMapping("/admin-demo")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin-only demo endpoint", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<String> adminOnlyDemo() {
        return ApiResponse.ok("ADMIN only");
    }

    @GetMapping("/user-demo")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "User or admin demo endpoint", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<String> userOrAdminDemo() {
        return ApiResponse.ok("ADMIN or USER");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product detail (cache-aside + Redis lock)", security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<ProductResponse> getProduct(@PathVariable Long id) {
        return ApiResponse.ok(productService.getProduct(id));
    }
}
