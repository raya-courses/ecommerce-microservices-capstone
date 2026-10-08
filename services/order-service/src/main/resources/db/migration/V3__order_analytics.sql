-- V3__order_analytics.sql: Order Analytics read model & idempotency tracking

CREATE TABLE IF NOT EXISTS order_analytics (
    order_id VARCHAR(255) PRIMARY KEY,
    product_id VARCHAR(255),
    customer_id VARCHAR(255),
    amount NUMERIC(38,2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS analytics_processed_events (
    event_id VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS hourly_order_metrics (
    id BIGSERIAL PRIMARY KEY,
    hour_bucket VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    order_count BIGINT NOT NULL DEFAULT 0,
    revenue NUMERIC(38,2) NOT NULL DEFAULT 0,
    CONSTRAINT uq_hour_status UNIQUE (hour_bucket, status)
);
