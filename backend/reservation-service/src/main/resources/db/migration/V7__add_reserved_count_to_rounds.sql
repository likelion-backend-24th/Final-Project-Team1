-- 정원 차감을 위한 컬럼. 예약 인원의 합을 회차 행에 직접 들고 있는다.
--
-- reservations 를 COUNT 해서 잔여 정원을 구하지 않는 이유는 두 가지다.
--   - 조회 때마다 집계가 돌아 회차 목록 응답이 예약 건수에 비례해 느려진다.
--   - 무엇보다, 정원 초과 판정을 DB 가 원자적으로 하지 못한다. 아래 CHECK 와
--     조건부 UPDATE 를 함께 쓰면 락 없이 초과를 구조적으로 막을 수 있다.
--
-- 이 컬럼은 반드시 조건부 UPDATE 로만 바꾼다(#76 차감, #77 만료 반환, #83 취소 반환).
--   UPDATE rounds SET reserved_count = reserved_count + :n
--    WHERE id = :id AND reserved_count + :n <= capacity
-- 갱신 행 수가 0 이면 정원 초과다. expo-service 자동 마감 스케줄러와 같은 방식이다.

ALTER TABLE rounds
    ADD COLUMN reserved_count INT NOT NULL DEFAULT 0
        COMMENT 'PENDING·CONFIRMED 예약 인원의 합. 조건부 UPDATE 로만 변경한다' AFTER capacity;

-- CHECK 는 최종 방어선이다. 조건부 UPDATE 를 빠뜨린 코드가 들어와도 DB 가 거절한다.
ALTER TABLE rounds
    ADD CONSTRAINT ck_rounds_reserved_count CHECK (reserved_count >= 0 AND reserved_count <= capacity);
