CREATE TABLE app_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(120) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_app_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone)
);

CREATE INDEX idx_users_role_status ON app_users (role, status);

CREATE TABLE locker_stations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_locker_stations PRIMARY KEY (id)
);

CREATE INDEX idx_locker_stations_status ON locker_stations (status);

CREATE TABLE locker_cells (
    id BIGINT NOT NULL AUTO_INCREMENT,
    station_id BIGINT NOT NULL,
    cell_code VARCHAR(20) NOT NULL,
    size VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_locker_cells PRIMARY KEY (id),
    CONSTRAINT fk_locker_cells_station FOREIGN KEY (station_id) REFERENCES locker_stations (id),
    CONSTRAINT uk_locker_cells_station_code UNIQUE (station_id, cell_code)
);

CREATE INDEX idx_locker_cells_station ON locker_cells (station_id);
CREATE INDEX idx_locker_cells_status_size ON locker_cells (status, size);

CREATE TABLE parcels (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tracking_number VARCHAR(64) NOT NULL,
    customer_id BIGINT NOT NULL,
    courier_id BIGINT NOT NULL,
    locker_cell_id BIGINT NULL,
    size VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    stored_at DATETIME(6) NULL,
    picked_up_at DATETIME(6) NULL,
    expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_parcels PRIMARY KEY (id),
    CONSTRAINT uk_parcels_tracking_number UNIQUE (tracking_number),
    CONSTRAINT fk_parcels_customer FOREIGN KEY (customer_id) REFERENCES app_users (id),
    CONSTRAINT fk_parcels_courier FOREIGN KEY (courier_id) REFERENCES app_users (id),
    CONSTRAINT fk_parcels_locker_cell FOREIGN KEY (locker_cell_id) REFERENCES locker_cells (id)
);

CREATE INDEX idx_parcels_customer ON parcels (customer_id);
CREATE INDEX idx_parcels_courier ON parcels (courier_id);
CREATE INDEX idx_parcels_locker_cell ON parcels (locker_cell_id);
CREATE INDEX idx_parcels_status ON parcels (status);
CREATE INDEX idx_parcels_status_expires ON parcels (status, expires_at);

