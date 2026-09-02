CREATE TABLE users (
                       id            BIGINT       NOT NULL AUTO_INCREMENT,
                       email         VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(60)  NOT NULL,
                       name          VARCHAR(100) NOT NULL,
                       created_at    DATETIME     NOT NULL,
                       PRIMARY KEY (id),
                       UNIQUE KEY uk_users_email (email)
);

CREATE TABLE user_roles (
                            user_id    BIGINT      NOT NULL,
                            role       VARCHAR(20) NOT NULL,
                            granted_at DATETIME    NOT NULL,
                            PRIMARY KEY (user_id, role),
                            CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id)
);