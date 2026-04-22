CREATE DATABASE otpdb;

\c otpdb;

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    login VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    telegram_chat_id VARCHAR(50)
);

CREATE TABLE otp_config (
    id INTEGER PRIMARY KEY DEFAULT 1,
    code_length INTEGER NOT NULL DEFAULT 6,
    ttl_seconds INTEGER NOT NULL DEFAULT 300,
    CONSTRAINT single_row CHECK (id = 1)
);

INSERT INTO otp_config (code_length, ttl_seconds) VALUES (6, 300)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE otp_codes (
    id SERIAL PRIMARY KEY,
    operation_id VARCHAR(100) NOT NULL,
    code VARCHAR(20) NOT NULL,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED', 'USED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_otp_codes_operation_user ON otp_codes(operation_id, user_id);
