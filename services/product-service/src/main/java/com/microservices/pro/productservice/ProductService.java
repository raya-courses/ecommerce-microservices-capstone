package com.microservices.pro.productservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * ProductService.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CacheManager cacheManager;

    public ProductService(ProductRepository productRepository,
                          @Autowired(required = false) CacheManager cacheManager) {
        this.productRepository = productRepository;
        this.cacheManager = cacheManager;
    }

    @Cacheable(value = "products", key = "'all'")
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Cacheable(value = "products", key = "#id")
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    public Product save(Product product) {
        Product saved = productRepository.save(product);
        evictProductsCache(saved.getId());
        return saved;
    }

    public void deleteById(Long id) {
        productRepository.deleteById(id);
        evictProductsCache(id);
    }

    public void evictAllProductsCache() {
        evictProductsCache(null);
    }

    private void evictProductsCache(Long id) {
        if (cacheManager != null) {
            var cache = cacheManager.getCache("products");
            if (cache != null) {
                if (id != null) {
                    cache.evict(id);
                }
                cache.evict("all");
                cache.evict("all-summary");
                cache.clear();
            }
        }
    }
}
