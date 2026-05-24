CREATE TABLE IF NOT EXISTS inventory (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_code    VARCHAR(64)  NOT NULL UNIQUE,
    available_stock INT          NOT NULL,
    reserved_stock  INT          NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
