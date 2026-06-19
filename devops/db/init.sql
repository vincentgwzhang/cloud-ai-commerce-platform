-- create database commerce_platform;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Password for all sample users below: 123456

INSERT INTO users (username, password, role, enabled)
VALUES
(
  'admin',
  '$2a$10$uuDx3I721W9gWiUIq0gx6.trwrSkh/zsHLxDQuFJTdM/XbKfti2sm',
  'ADMIN',
  true
),
(
  'vincent',
  '$2a$10$uuDx3I721W9gWiUIq0gx6.trwrSkh/zsHLxDQuFJTdM/XbKfti2sm',
  'USER',
  true
),
(
  'tester',
  '$2a$10$uuDx3I721W9gWiUIq0gx6.trwrSkh/zsHLxDQuFJTdM/XbKfti2sm',
  'USER',
  true
)
ON DUPLICATE KEY UPDATE username = username;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token VARCHAR(500) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_refresh_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
);

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
)
ON DUPLICATE KEY UPDATE product_code = product_code;

CREATE TABLE IF NOT EXISTS inventory (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_code    VARCHAR(64)  NOT NULL UNIQUE,
    available_stock INT          NOT NULL,
    reserved_stock  INT          NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_available_non_negative CHECK (available_stock >= 0),
    CONSTRAINT chk_reserved_non_negative CHECK (reserved_stock >= 0)
);

INSERT INTO inventory (product_code, available_stock, reserved_stock, version)
VALUES
    ('IPHONE17', 100, 0, 0),
    ('RTX5090', 20, 0, 0),
    ('PS6', 50, 0, 0)
ON DUPLICATE KEY UPDATE product_code = product_code;

CREATE TABLE IF NOT EXISTS orders (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no     VARCHAR(64)  NOT NULL UNIQUE,
    product_code VARCHAR(64)  NOT NULL,
    -- Authenticated buyer (JWT subject). Nullable: demo/unauthenticated flows create orders
    -- without a principal. Propagated downstream via order-created so ai-service can attribute
    -- per-user behaviour signals.
    username     VARCHAR(50)  NULL,
    quantity     INT          NOT NULL,
    amount       DECIMAL(12, 2) NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    request_id   VARCHAR(64)  NOT NULL UNIQUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_orders_status (status)
);

-- ai-service: behaviour signals derived from order/inventory events (recommendation input)
CREATE TABLE IF NOT EXISTS ai_interaction (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    username         VARCHAR(50)  NOT NULL,
    product_code     VARCHAR(64)  NOT NULL,
    interaction_type VARCHAR(20)  NOT NULL,
    weight           INT          NOT NULL DEFAULT 1,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ai_interaction_user (username),
    KEY idx_ai_interaction_product (product_code)
);

-- ai-service: recommendation audit log (explainability / observability)
CREATE TABLE IF NOT EXISTS ai_recommendation_log (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    username          VARCHAR(50)  NOT NULL,
    context           VARCHAR(200),
    recommended_codes VARCHAR(500) NOT NULL,
    source            VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ai_reco_user (username)
);
