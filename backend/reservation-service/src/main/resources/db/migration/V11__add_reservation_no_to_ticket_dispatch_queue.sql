-- 티켓 발급 통지에 예약번호를 함께 실어 보낸다(Story 7 이월분, 계약 1-1 변경).
--
-- 큐가 이미 expo_id·round_id·user_id·headcount 를 비정규화해 들고 있는 것과 같은 이유로
-- 예약번호도 여기 둔다. Dispatcher 가 발송 시점에 예약을 다시 조회하지 않아도 된다.
--
-- NULL 을 허용하는 이유: 이미 쌓인 통지 행에는 채울 값이 없다. 신규 적재는 애플리케이션이 강제한다.
-- UNIQUE 는 걸지 않는다 - 한 예약이 발급 1건과 무효화 1건을 가질 수 있어 예약번호가 두 번 나온다.

ALTER TABLE ticket_dispatch_queue
    ADD COLUMN reservation_no VARCHAR(20) NULL
        COMMENT '발급 통지에 함께 보낼 예약번호. 발송 시점 재조회를 피하려고 비정규화' AFTER reservation_id;
