package com.microservices.pro.productservice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * ProductRepository — Session 1 (homework), Session 8 (JPA), Session 18 (CQRS Projections).
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p.id as id, p.name as name, p.category as category, p.price as price FROM Product p")
    List<ProductSummaryProjection> findAllSummaries();

    @Query("SELECT p.id as id, p.name as name, p.category as category, p.price as price FROM Product p WHERE p.id = :id")
    Optional<ProductSummaryProjection> findSummaryById(@Param("id") Long id);
}
