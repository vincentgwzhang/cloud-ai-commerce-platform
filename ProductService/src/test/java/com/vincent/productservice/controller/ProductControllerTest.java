package com.vincent.productservice.controller;

import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.entity.ProductStatus;
import com.vincent.productservice.service.ProductService;
import com.vincent.productservice.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
// @AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/products/health").with(testUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void listProductsReturnsWrappedResponse() throws Exception {
        ProductResponse product = sampleProduct(1L);
        when(productService.listActiveProducts()).thenReturn(List.of(product));

        mockMvc.perform(get("/api/v1/products")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(testUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("iPhone 17"));
    }

    @Test
    void getProductById() throws Exception {
        when(productService.getProduct(1L)).thenReturn(sampleProduct(1L));

        mockMvc.perform(get("/api/v1/products/1").with(testUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCode").value("IPHONE-17"));
    }

    @Test
    void adminOnlyDemoReturnsWrappedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/products/admin-demo").with(testAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ADMIN only"));
    }

    @Test
    void userOrAdminDemoReturnsWrappedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/products/user-demo").with(testUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ADMIN or USER"));
    }

    private static RequestPostProcessor testUser() {
        return jwt()
                .jwt(token -> token
                        .issuer("auth-service-test")
                        .subject("vincent")
                        .claim("roles", List.of("USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static RequestPostProcessor testAdmin() {
        TestUser user = new TestUser(
            "vincent", 
            "password", 
            "ADMIN", 
            true
        );
        return user(user);
    }

    private static ProductResponse sampleProduct(Long id) {
        return new ProductResponse(
                id,
                "IPHONE-17",
                "iPhone 17",
                "desc",
                new BigDecimal("999.00"),
                10,
                ProductStatus.ACTIVE,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
