INSERT INTO users (email, password_hash, name, created_at)
VALUES ('${superAdminEmail}', '${superAdminPasswordHash}', '전체관리자', UTC_TIMESTAMP());

INSERT INTO user_roles (user_id, role, granted_at)
VALUES (LAST_INSERT_ID(), 'SUPER_ADMIN', UTC_TIMESTAMP());
