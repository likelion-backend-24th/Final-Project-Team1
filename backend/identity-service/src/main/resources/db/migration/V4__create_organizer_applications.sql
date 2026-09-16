CREATE TABLE organizer_applications (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason        TEXT,
    reject_reason TEXT,
    reviewer_id   BIGINT,
    reviewed_at   DATETIME,
    created_at    DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_oa_user FOREIGN KEY (user_id) REFERENCES users (id)
);
