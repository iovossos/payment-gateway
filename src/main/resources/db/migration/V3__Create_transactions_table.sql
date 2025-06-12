CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    gateway_response TEXT,
    gateway_transaction_id VARCHAR(100),
    processed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_transactions_payment_id ON transactions(payment_id);
CREATE INDEX idx_transactions_status ON transactions(status);