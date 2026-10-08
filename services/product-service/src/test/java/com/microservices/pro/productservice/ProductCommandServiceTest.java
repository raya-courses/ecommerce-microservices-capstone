package com.microservices.pro.productservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCommandServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProductCommandService commandService;

    @Test
    void createProduct_savesProduct_andPublishesCreatedEvent() {
        Product saved = new Product(1L, "Laptop Pro", "High-end laptop",
                new BigDecimal("1299.99"), "ELECTRONICS");
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        Product result = commandService.createProduct(
                "Laptop Pro", "High-end laptop",
                new BigDecimal("1299.99"), "ELECTRONICS");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Laptop Pro");

        ArgumentCaptor<ProductChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ProductChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().changeType()).isEqualTo("CREATED");
        assertThat(eventCaptor.getValue().productId()).isEqualTo(1L);
    }

    @Test
    void createProduct_throwsException_whenPriceIsZeroOrNegative() {
        assertThatThrownBy(() ->
                commandService.createProduct("Laptop", "desc",
                        BigDecimal.ZERO, "ELECTRONICS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price must be greater than zero");
    }

    @Test
    void updateProduct_updatesFields_andPublishesUpdatedEvent() {
        Product existing = new Product(1L, "Old Laptop", "Old desc",
                new BigDecimal("999.99"), "ELECTRONICS");
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = commandService.updateProduct(
                1L, "New Laptop", "New desc",
                new BigDecimal("1199.99"), "COMPUTERS");

        assertThat(result.getName()).isEqualTo("New Laptop");
        assertThat(result.getDescription()).isEqualTo("New desc");
        assertThat(result.getPrice()).isEqualTo(new BigDecimal("1199.99"));
        assertThat(result.getCategory()).isEqualTo("COMPUTERS");

        ArgumentCaptor<ProductChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ProductChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().changeType()).isEqualTo("UPDATED");
        assertThat(eventCaptor.getValue().productId()).isEqualTo(1L);
    }

    @Test
    void updateProduct_throwsException_whenNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                commandService.updateProduct(99L, "Laptop", "desc",
                        BigDecimal.TEN, "ELECTRONICS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found: 99");
    }

    @Test
    void deleteProduct_deletesAndPublishesDeletedEvent() {
        when(productRepository.existsById(1L)).thenReturn(true);

        commandService.deleteProduct(1L);

        verify(productRepository).deleteById(1L);
        ArgumentCaptor<ProductChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ProductChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().changeType()).isEqualTo("DELETED");
        assertThat(eventCaptor.getValue().productId()).isEqualTo(1L);
    }

    @Test
    void deleteProduct_throwsException_whenNotFound() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> commandService.deleteProduct(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found: 99");
    }
}
