-- 취소된 예약의 티켓 무효화도 같은 큐로 나른다(#79 계약 1-2).
-- 무효화가 실패하면 취소된 예약으로 입장이 가능해진다 - 발급 실패보다 나쁜 방향이라
-- 재시도가 더 필요하다.
--
-- UNIQUE 를 (reservation_id, type) 로 옮긴다. 한 예약이 발급 1건과 무효화 1건을
-- 가질 수 있어야 하고, 같은 종류가 두 번 쌓이는 것은 여전히 막아야 한다.

ALTER TABLE ticket_dispatch_queue
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'ISSUE' AFTER reservation_id;

ALTER TABLE ticket_dispatch_queue
    DROP INDEX uk_ticket_dispatch_reservation_id;

ALTER TABLE ticket_dispatch_queue
    ADD CONSTRAINT uk_ticket_dispatch_reservation_type UNIQUE (reservation_id, type);

ALTER TABLE ticket_dispatch_queue
    ADD CONSTRAINT ck_ticket_dispatch_type CHECK (type IN ('ISSUE', 'REVOKE'));
