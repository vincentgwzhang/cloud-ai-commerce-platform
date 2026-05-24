CREATE TABLE IF NOT EXISTS products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    price DECIMAL(12, 2) NOT NULL,
    stock INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO products (product_code, name, description, price, stock, status, created_at, updated_at)
VALUES
(
    'IPHONE-17',
    'iPhone 17',
    'Latest Apple smartphone for the commerce platform POC.',
    999.00,
    120,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'RTX-5090',
    'NVIDIA RTX 5090',
    'High-end GPU for AI and gaming workloads.',
    1999.00,
    35,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'PS6',
    'PlayStation 6',
    'Next-gen console — fictional SKU for demo catalog.',
    599.00,
    80,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
