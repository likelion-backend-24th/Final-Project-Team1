CREATE TABLE channels (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    owner_id    BIGINT       NOT NULL,
    description TEXT,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_channels_name (name),
    INDEX idx_channels_owner_id (owner_id)
);
