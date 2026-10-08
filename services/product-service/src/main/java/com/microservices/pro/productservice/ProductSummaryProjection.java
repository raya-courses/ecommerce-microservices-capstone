package com.microservices.pro.productservice;

import java.math.BigDecimal;

/**
 * ProductSummaryProjection — Session 18, CQRS Read Model.
 *
 * Spring Data interface-based projection: Spring generates the implementation
 * from a JPQL query defined in ProductRepository. Shaped for what the
 * CONSUMER needs (summary and detail view), not what the writer stores.
 */
public interface ProductSummaryProjection {
    Long getId();
    String getName();
    String getDescription();
    String getCategory();
    BigDecimal getPrice();
}
