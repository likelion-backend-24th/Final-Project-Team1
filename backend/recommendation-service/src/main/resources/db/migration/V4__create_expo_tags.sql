CREATE TABLE expo_tags (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    expo_id    BIGINT       NOT NULL,
    tag_value  VARCHAR(100) NOT NULL,
    summary    TEXT,
    created_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_et (expo_id, tag_value),
    INDEX idx_et_expo (expo_id)
);
