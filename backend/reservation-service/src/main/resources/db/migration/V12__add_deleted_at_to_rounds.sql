-- 회차 소프트 삭제(S9-3).
--
-- 왜 하드 삭제가 아닌가: reservations.round_id 에 FK 가 걸려 있어, 예약이 들어왔다 전부
-- 취소된 회차는 reserved_count = 0 이어도 DELETE 가 FK 위반으로 실패한다.
-- 수정은 되는데 삭제만 안 되는 상황이 된다. 취소·환불 이력도 보존해야 한다.
--
-- 인덱스: 살아있는 회차만 읽는 조회가 대부분이라 기존 expo_id 조회에 deleted_at 을 얹는다.

ALTER TABLE rounds
    ADD COLUMN deleted_at DATETIME(6) NULL COMMENT 'UTC. NULL 이면 살아있는 회차' AFTER created_at;

CREATE INDEX idx_rounds_expo_id_deleted_at ON rounds (expo_id, deleted_at);
