CREATE TABLE user_activities (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    expo_id     BIGINT      NOT NULL,
    event_type  VARCHAR(30) NOT NULL COMMENT 'RESERVATION_CONFIRMED | CHECKED_IN',
    occurred_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_ua_user (user_id),
    INDEX idx_ua_expo (expo_id)
);
