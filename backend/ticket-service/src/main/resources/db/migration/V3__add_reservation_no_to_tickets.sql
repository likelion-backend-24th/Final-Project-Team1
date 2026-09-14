-- 예약번호로도 체크인할 수 있게 한다(Story 7 이월분).
-- QR 을 못 쓰는 상황(휴대폰 방전·화면 손상·메일 분실)에서 주최자가 예약번호를 입력해
-- 티켓을 찾아야 하는데, tickets 에는 reservation_id 만 있어 찾을 방법이 없었다.
--
-- 조회 API 를 호출하지 않고 발급 시 함께 저장하는 이유는, 체크인 경로에 외부 호출을 넣으면
-- Reservation-Service 가 흔들릴 때 현장 입장 줄이 멈추기 때문이다. 예약번호는 불변이라
-- 회차 시각과 달리 저장해도 낡지 않는다.
--
-- NULL 을 허용하는 이유: 이미 발급된 티켓에는 채울 값이 없다(예약번호는 reservation 스키마에 있어
-- 이 DB 안에서 백필할 수 없다). MySQL 은 UNIQUE 컬럼에 NULL 을 여러 행 허용하므로 제약은 지금 건다.
-- 신규 발급은 애플리케이션이 값을 강제하며, 기존 행을 정리한 뒤 NOT NULL 로 조일 수 있다.

ALTER TABLE tickets
    ADD COLUMN reservation_no VARCHAR(20) NULL
        COMMENT '논리 참조 reservation.reservations.reservation_no - 예약번호 수동 체크인용' AFTER reservation_id;

ALTER TABLE tickets
    ADD CONSTRAINT uk_tickets_reservation_no UNIQUE (reservation_no);
