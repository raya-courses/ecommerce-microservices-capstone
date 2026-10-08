package com.microservices.pro.productservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductQueryService productQueryService;

    @MockBean
    private ProductCommandService productCommandService;

    @Autowired
    private ObjectMapper objectMapper;

    static class TestProjection implements ProductSummaryProjection {
        private final Long id;
        private final String name;
        private final String description;
        private final String category;
        private final BigDecimal price;

        public TestProjection(Long id, String name, String description, String category, BigDecimal price) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
            this.price = price;
        }

        @Override public Long getId() { return id; }
        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public String getCategory() { return category; }
        @Override public BigDecimal getPrice() { return price; }
    }

    @Test
    void getAllProducts_returnsPageableProductsList() throws Exception {
        TestProjection projection = new TestProjection(1L, "Widget", "A fine widget", "Widgets", new BigDecimal("19.99"));
        when(productQueryService.findAll(any()))
                .thenReturn(new PageImpl<>(List.of(projection), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/products?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Widget"))
                .andExpect(jsonPath("$.content[0].category").value("Widgets"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getProductById_whenFound_returnsProduct() throws Exception {
        TestProjection projection = new TestProjection(1L, "Widget", "A fine widget", "Widgets", new BigDecimal("19.99"));
        when(productQueryService.findSummaryById(1L)).thenReturn(Optional.of(projection));

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Widget"))
                .andExpect(jsonPath("$.category").value("Widgets"));
    }

    @Test
    void getProductById_whenNotFound_returns404() throws Exception {
        when(productQueryService.findSummaryById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/products/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createProduct_returns201AndSavedProduct() throws Exception {
        ProductRequest request = new ProductRequest("Gadget", "A cool gadget", new BigDecimal("29.99"), "Gadgets");
        Product saved = new Product(2L, "Gadget", "A cool gadget", new BigDecimal("29.99"), "Gadgets");
        TestProjection projection = new TestProjection(2L, "Gadget", "A cool gadget", "Gadgets", new BigDecimal("29.99"));

        when(productCommandService.createProduct(eq("Gadget"), eq("A cool gadget"), eq(new BigDecimal("29.99")), eq("Gadgets")))
                .thenReturn(saved);
        when(productQueryService.findSummaryById(2L)).thenReturn(Optional.of(projection));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Gadget"))
                .andExpect(jsonPath("$.category").value("Gadgets"));
    }

    @Test
    void updateProduct_returns200AndUpdatedProduct() throws Exception {
        ProductRequest request = new ProductRequest("Gadget Pro", "An updated gadget", new BigDecimal("39.99"), "Gadgets");
        Product updated = new Product(2L, "Gadget Pro", "An updated gadget", new BigDecimal("39.99"), "Gadgets");
        TestProjection projection = new TestProjection(2L, "Gadget Pro", "An updated gadget", "Gadgets", new BigDecimal("39.99"));

        when(productCommandService.updateProduct(eq(2L), eq("Gadget Pro"), eq("An updated gadget"), eq(new BigDecimal("39.99")), eq("Gadgets")))
                .thenReturn(updated);
        when(productQueryService.findSummaryById(2L)).thenReturn(Optional.of(projection));

        mockMvc.perform(put("/api/v1/products/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Gadget Pro"))
                .andExpect(jsonPath("$.category").value("Gadgets"));
    }

    @Test
    void deleteProduct_returns204() throws Exception {
        doNothing().when(productCommandService).deleteProduct(1L);

        mockMvc.perform(delete("/api/v1/products/1"))
                .andExpect(status().isNoContent());

        verify(productCommandService).deleteProduct(1L);
    }
}
