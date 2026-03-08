CREATE TABLE coupons (
    id        BINARY(16)  NOT NULL,
    store_id  BINARY(16)  NOT NULL,
    user_id   BINARY(16)  NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_store_user (store_id, user_id),
    CONSTRAINT fk_coupon_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_coupon_user  FOREIGN KEY (user_id)  REFERENCES app_users (id),
    INDEX idx_coupons_store_id (store_id)
);
