-- 예약 확정 알림(#231)을 담기 위해 알림 종류와 중복 방지 키를 둔다.
-- 기존 UNIQUE(user_id, expo_id) 는 추천 알림(#193) 규칙이라 예약 알림과 함께 쓸 수 없다.
--   추천 알림:      dedup_key = RECOMMENDATION:{expoId}        → 같은 박람회 중복 금지(기존 규칙 유지)
--   예약 확정 알림: dedup_key = RESERVATION_CONFIRMED:{reservationId} → 재전송돼도 1건
ALTER TABLE notifications
    ADD COLUMN type      VARCHAR(30)  NOT NULL DEFAULT 'RECOMMENDATION' AFTER expo_id,
    ADD COLUMN dedup_key VARCHAR(100) NULL AFTER type;

UPDATE notifications
SET dedup_key = CONCAT('RECOMMENDATION:', expo_id)
WHERE dedup_key IS NULL;

ALTER TABLE notifications
    MODIFY COLUMN dedup_key  VARCHAR(100) NOT NULL,
    MODIFY COLUMN created_at DATETIME(6)  NOT NULL COMMENT 'UTC',
    DROP INDEX uq_notif,
    ADD UNIQUE KEY uq_notif_dedup (user_id, dedup_key);
