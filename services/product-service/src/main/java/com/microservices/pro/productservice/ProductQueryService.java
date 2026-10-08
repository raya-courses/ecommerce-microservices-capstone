package com.microservices.pro.productservice;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * ProductQueryService — Session 18, Lab 13B.
 *
 * QUERY side of the CQRS split. Owns all READ operations on products:
 *   - Returns data shaped for the consumer (ProductSummaryProjection)
 *   - Marked @Transactional(readOnly = true)
 *   - @Cacheable annotations live HERE
 */
@Service
public class ProductQueryService {

    private final ProductRepository productRepository;

    public ProductQueryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryProjection> findAll(Pageable pageable) {
        return productRepository.findAllSummaries(pageable);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "'all-summary'")
    public List<ProductSummaryProjection> findAllSummaries() {
        return productRepository.findAllSummaries();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#id")
    public Optional<ProductSummaryProjection> findSummaryById(Long id) {
        return productRepository.findSummaryById(id);
    }
}
