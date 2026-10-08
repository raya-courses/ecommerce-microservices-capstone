CREATE TABLE IF NOT EXISTS stock_items (
    product_id VARCHAR(255) PRIMARY KEY,
    available_quantity INT NOT NULL,
    reserved_quantity INT NOT NULL
);

CREATE TABLE IF NOT EXISTS stock_reservations (
    order_id VARCHAR(255) PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL,
    quantity INT NOT NULL
);

INSERT INTO stock_items (product_id, available_quantity, reserved_quantity) VALUES
    ('PROD-001', 100, 0),
    ('PROD-002', 5, 0),
    ('PROD-003', 0, 0)
ON CONFLICT (product_id) DO NOTHING;
