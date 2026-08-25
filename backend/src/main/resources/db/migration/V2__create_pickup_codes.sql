CREATE TABLE pickup_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    parcel_id BIGINT NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_pickup_codes PRIMARY KEY (id),
    CONSTRAINT fk_pickup_codes_parcel FOREIGN KEY (parcel_id) REFERENCES parcels (id)
);

CREATE INDEX idx_pickup_codes_parcel_status ON pickup_codes (parcel_id, status);
CREATE INDEX idx_pickup_codes_status_expires ON pickup_codes (status, expires_at);
