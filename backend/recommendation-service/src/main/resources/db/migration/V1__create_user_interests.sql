CREATE TABLE user_interests (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       VARCHAR(10)  NOT NULL COMMENT 'CATEGORY | KEYWORD',
    value      VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_interest (user_id, type, value)
);
