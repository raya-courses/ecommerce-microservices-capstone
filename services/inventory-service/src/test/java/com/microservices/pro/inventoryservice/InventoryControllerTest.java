package com.microservices.pro.inventoryservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void checkStock_whenAvailable_returns200Ok() throws Exception {
        when(inventoryService.checkStock("PROD-001", 2))
                .thenReturn(new StockCheckResponse("PROD-001", 2, true, 8));

        mockMvc.perform(get("/api/v1/inventory/check")
                        .param("productId", "PROD-001")
                        .param("quantity", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("PROD-001"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.remainingStock").value(8));
    }

    @Test
    void checkStock_whenNotAvailable_returns409Conflict() throws Exception {
        when(inventoryService.checkStock("PROD-003", 5))
                .thenReturn(new StockCheckResponse("PROD-003", 5, false, 0));

        mockMvc.perform(get("/api/v1/inventory/check")
                        .param("productId", "PROD-003")
                        .param("quantity", "5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.productId").value("PROD-003"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.remainingStock").value(0));
    }

    @Test
    void getStock_whenFound_returns200Ok() throws Exception {
        StockItem item = new StockItem("PROD-001", 50, 5);
        when(inventoryService.getStock("PROD-001")).thenReturn(Optional.of(item));

        mockMvc.perform(get("/api/v1/inventory/PROD-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("PROD-001"))
                .andExpect(jsonPath("$.availableQuantity").value(50))
                .andExpect(jsonPath("$.reservedQuantity").value(5));
    }

    @Test
    void getStock_whenNotFound_returns404() throws Exception {
        when(inventoryService.getStock("PROD-999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/inventory/PROD-999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStock_updatesAndReturnsStockItem() throws Exception {
        StockAdjustmentRequest request = new StockAdjustmentRequest(100);
        StockItem updated = new StockItem("PROD-001", 100, 0);
        when(inventoryService.updateStock("PROD-001", 100)).thenReturn(updated);

        mockMvc.perform(put("/api/v1/inventory/PROD-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value("PROD-001"))
                .andExpect(jsonPath("$.availableQuantity").value(100));
    }
}
