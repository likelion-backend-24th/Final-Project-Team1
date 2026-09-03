-- role 값을 Java Role enum과 동일한 3종으로 DB에서도 제한한다.
-- 애플리케이션을 거치지 않는 직접 INSERT까지 막는 것이 목적이다.
ALTER TABLE user_roles
    ADD CONSTRAINT ck_user_roles_role
        CHECK (role IN ('SUPER_ADMIN', 'ORGANIZER', 'USER'));
