CREATE TABLE IF NOT EXISTS product (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    price NUMERIC(38,2) NOT NULL,
    category VARCHAR(255)
);

INSERT INTO product (name, description, price, category) VALUES
    ('Mechanical Keyboard', 'RGB Backlit Mechanical Keyboard', 89.99, 'ELECTRONICS'),
    ('Wireless Mouse', 'Ergonomic 2.4GHz Wireless Mouse', 29.99, 'ELECTRONICS'),
    ('Gaming Monitor', '27-inch 144Hz IPS Gaming Monitor', 299.99, 'ELECTRONICS')
ON CONFLICT DO NOTHING;
