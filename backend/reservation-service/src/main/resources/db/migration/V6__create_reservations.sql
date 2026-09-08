-- Reservation-Service 소유 스키마: reservation
-- 예약(Reservation Aggregate Root). 상태는 PENDING → CONFIRMED / CANCELLED / EXPIRED 로 전이한다.
-- 정원 차감·반환은 상태 전이가 성공한 경우에만 1회 일어난다(#76 차감, #77 만료 반환, #83 취소 반환).
--
-- 파일 번호가 V2 가 아니라 V6 인 이유: 결제 모듈(#96)의 V4·V5 가 먼저 적용되어
-- 스키마 현재 버전이 5 다. 그보다 낮은 번호를 새로 넣으면 Flyway 가 out-of-order 로 거절한다.

CREATE TABLE reservations
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    reservation_no VARCHAR(20)  NOT NULL COMMENT '사람이 읽고 입력하는 예약번호. R-XXXX-XXXX 형식',
    round_id       BIGINT       NOT NULL COMMENT '같은 스키마의 rounds.id - FK 있음',
    expo_id        BIGINT       NOT NULL COMMENT '논리 참조 expo.expos.id - FK 아님(다른 Service DB)',
    user_id        BIGINT       NOT NULL COMMENT '논리 참조 identity.users.id - FK 아님(다른 Service DB)',
    contact_name   VARCHAR(100) NOT NULL COMMENT '예약 시점 입력값. 대리 예약이 가능하므로 users 를 조회하지 않는다',
    contact_phone  VARCHAR(20)  NOT NULL COMMENT '하이픈을 제거한 숫자만 저장해 검색이 표기 형식을 타지 않게 한다',
    headcount      INT          NOT NULL COMMENT '예약 인원. 1 이상',
    amount         INT          NOT NULL COMMENT '결제 금액 = headcount * rounds.fee. 무료 회차는 0',
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at     DATETIME(6)  NOT NULL COMMENT 'UTC',
    expires_at     DATETIME(6)  NOT NULL COMMENT 'UTC. 결제 대기 만료 시각(생성 +10분). EXPIRED 로 끝난 예약은 이 값이 곧 종료 시각이다',
    confirmed_at   DATETIME(6)  NULL COMMENT 'UTC',
    cancelled_at   DATETIME(6)  NULL COMMENT 'UTC. CANCELLED 에만 채운다 - EXPIRED 의 종료 시각은 expires_at 이므로 별도 컬럼을 두지 않는다',
    PRIMARY KEY (id),
    CONSTRAINT uk_reservations_reservation_no UNIQUE (reservation_no),

    -- round_id Index 를 FK 보다 먼저 선언한다. MySQL 은 FK 에 쓸 Index 가 없으면 자기가 하나 더 만들기 때문에,
    -- 뒤에 CREATE INDEX 로 붙이면 같은 컬럼에 Index 가 두 벌 생긴다.
    KEY idx_reservations_round_id (round_id),
    KEY idx_reservations_user_id (user_id),
    KEY idx_reservations_status_expires_at (status, expires_at),

    CONSTRAINT fk_reservations_round_id FOREIGN KEY (round_id) REFERENCES rounds (id),
    CONSTRAINT ck_reservations_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_reservations_headcount CHECK (headcount >= 1),
    CONSTRAINT ck_reservations_amount CHECK (amount >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
