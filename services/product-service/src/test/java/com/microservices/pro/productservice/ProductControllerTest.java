package com.microservices.pro.productservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllProducts_returnsProductsList() throws Exception {
        Product p = new Product(1L, "Widget", "A fine widget", new BigDecimal("19.99"), "Widgets");
        when(productService.findAll()).thenReturn(List.of(p));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Widget"));
    }

    @Test
    void getProductById_whenFound_returnsProduct() throws Exception {
        Product p = new Product(1L, "Widget", "A fine widget", new BigDecimal("19.99"), "Widgets");
        when(productService.findById(1L)).thenReturn(Optional.of(p));

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Widget"));
    }

    @Test
    void getProductById_whenNotFound_returns404() throws Exception {
        when(productService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/products/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProduct_returns201AndSavedProduct() throws Exception {
        Product input = new Product(null, "Gadget", "A cool gadget", new BigDecimal("29.99"), "Gadgets");
        Product saved = new Product(2L, "Gadget", "A cool gadget", new BigDecimal("29.99"), "Gadgets");
        when(productService.save(any(Product.class))).thenReturn(saved);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Gadget"));
    }

    @Test
    void deleteProduct_returns204() throws Exception {
        doNothing().when(productService).deleteById(1L);

        mockMvc.perform(delete("/api/v1/products/1"))
                .andExpect(status().isNoContent());

        verify(productService).deleteById(1L);
    }
}
