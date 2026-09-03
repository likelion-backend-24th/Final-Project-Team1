CREATE TABLE expos (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    channel_id    BIGINT       NOT NULL,
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    venue         VARCHAR(200),
    region        VARCHAR(50),
    category      VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'HIDDEN',
    thumbnail_url VARCHAR(500),
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    closed_at     DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_expos_channel FOREIGN KEY (channel_id) REFERENCES channels (id),
    INDEX idx_expos_channel_id (channel_id),
    INDEX idx_expos_status_region_category (status, region, category),
    INDEX idx_expos_status_closed_at (status, closed_at)
);
