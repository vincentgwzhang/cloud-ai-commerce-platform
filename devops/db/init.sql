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
