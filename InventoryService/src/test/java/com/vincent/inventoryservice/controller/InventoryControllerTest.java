package com.vincent.inventoryservice.controller;

import com.vincent.inventoryservice.dto.InventoryResponse;
import com.vincent.inventoryservice.service.InventoryConcurrencyDemoService;
import com.vincent.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    @MockitoBean
    private InventoryConcurrencyDemoService concurrencyDemoService;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/inventory/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void getInventoryReturnsWrappedResponse() throws Exception {
        when(inventoryService.getInventory("IPHONE17"))
                .thenReturn(new InventoryResponse("IPHONE17", 100, 0, 0L));

        mockMvc.perform(get("/api/inventory/IPHONE17").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCode").value("IPHONE17"));
    }

    @Test
    void reserveEndpointDelegatesToService() throws Exception {
        when(inventoryService.reserve("PS6", 1, "req-1"))
                .thenReturn(new InventoryResponse("PS6", 49, 1, 0L));

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"PS6","quantity":1,"requestId":"req-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("reserved"));
    }
}
