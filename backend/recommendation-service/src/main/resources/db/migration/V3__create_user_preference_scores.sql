CREATE TABLE user_preference_scores (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL,
    tag_value  VARCHAR(100)  NOT NULL,
    score      DECIMAL(10,4) NOT NULL DEFAULT 0,
    updated_at DATETIME      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ups (user_id, tag_value),
    INDEX idx_ups_user (user_id)
);
