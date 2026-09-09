-- 예약당 티켓 1건(API 계약 v3 확정사항 #17).
-- 1인 1코드(예약 인원 N → 티켓 N행)에서 예약당 1건으로 전환한다.
--  - headcount: 이 티켓 1건이 대응하는 예약 인원 = 입장 인원(코드는 1개지만 N명분).
--  - reservation_id UNIQUE: 예약당 1행이 되므로 유일. 발급 통지가 fail-open(재시도 전제)이라
--    이 유일 제약이 멱등의 근거가 된다("이미 있으면 새로 만들지 않고 기존 반환").
-- 주의: 기존 데이터에 같은 reservation_id 가 여러 행 있으면 UNIQUE 추가가 실패한다.
--       1인 1코드 시절 데이터가 남아 있으면 tickets 를 비운 뒤 적용한다(운영 데이터 없음 전제).

ALTER TABLE tickets
    ADD COLUMN headcount INT NOT NULL DEFAULT 1 COMMENT '예약 인원 = 입장 인원(티켓 1건 = N명분)';

ALTER TABLE tickets
    DROP INDEX idx_tickets_reservation_id;

ALTER TABLE tickets
    ADD CONSTRAINT uk_tickets_reservation_id UNIQUE (reservation_id);
