CREATE TABLE IF NOT EXISTS orders (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no     VARCHAR(64)  NOT NULL UNIQUE,
    product_code VARCHAR(64)  NOT NULL,
    quantity     INT          NOT NULL,
    amount       DECIMAL(12, 2) NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    request_id   VARCHAR(64)  NOT NULL UNIQUE,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);
