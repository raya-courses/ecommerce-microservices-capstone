package com.microservices.pro.productservice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ProductController — Session 1, Lab 1 / Session 18 CQRS.
 *
 * Reads delegate to ProductQueryService.
 * Writes delegate to ProductCommandService.
 *
 * Endpoints:
 *   GET    /api/v1/products       → pageable list of products (projections)
 *   GET    /api/v1/products/{id}  → product detail by id (projection, 404 if not found)
 *   POST   /api/v1/products       → create a new product (201 Created)
 *   PUT    /api/v1/products/{id}  → update an existing product (200 OK)
 *   DELETE /api/v1/products/{id}  → delete a product (204 No Content)
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductQueryService productQueryService;
    private final ProductCommandService productCommandService;

    public ProductController(ProductQueryService productQueryService,
                             ProductCommandService productCommandService) {
        this.productQueryService = productQueryService;
        this.productCommandService = productCommandService;
    }

    @GetMapping
    public ResponseEntity<Page<ProductSummaryProjection>> getAll(Pageable pageable) {
        return ResponseEntity.ok(productQueryService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductSummaryProjection> getById(@PathVariable Long id) {
        return productQueryService.findSummaryById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ProductSummaryProjection> create(@RequestBody ProductRequest request) {
        Product saved = productCommandService.createProduct(
                request.name(),
                request.description(),
                request.price(),
                request.category()
        );
        return productQueryService.findSummaryById(saved.getId())
                .map(p -> ResponseEntity.status(HttpStatus.CREATED).body(p))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CREATED).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductSummaryProjection> update(@PathVariable Long id,
                                                           @RequestBody ProductRequest request) {
        productCommandService.updateProduct(
                id,
                request.name(),
                request.description(),
                request.price(),
                request.category()
        );
        return productQueryService.findSummaryById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        productCommandService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
