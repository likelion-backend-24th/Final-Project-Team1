CREATE TABLE notifications (
    id         BIGINT     NOT NULL AUTO_INCREMENT,
    user_id    BIGINT     NOT NULL,
    expo_id    BIGINT     NOT NULL,
    message    TEXT       NOT NULL,
    is_read    TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_notif (user_id, expo_id),
    INDEX idx_notif_user (user_id)
);
