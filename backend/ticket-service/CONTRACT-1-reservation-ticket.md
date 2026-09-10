# 계약 1 — 예약 ↔ 티켓 (Reservation ⇄ Ticket) 내부 API

> Sprint 2. **A(Reservation-Service)** 가 호출하고 **C(Ticket-Service)** 가 제공한다.
> 서비스간 호출: `Authorization: Bearer <internal token>` + `X-Trace-Id` 헤더.
> 내부 경로는 **`/internal/v1/...`** 로 통일한다(API 계약 확정사항 #2·#9). nginx가 `/internal/**` 전체를 외부에서 차단한다.
> 성공 응답은 **raw**(봉투 없음)로 내보낸다. 에러만 `GlobalExceptionHandler` 가 봉투로 감싼다(expo·reservation 내부 API 와 동일).
> 정책: **예약당 티켓 1건**(API 계약 v3 확정사항 #17). 예약 인원(headcount) N 이어도 티켓은 1건이며, 그 1건이 N명분 입장을 대응한다.

## 1. 티켓 발급 — 예약 확정 시

`POST /internal/v1/tickets`

예약이 결제 확정되면 A가 호출한다. 예약당 **티켓 1건**을 발급한다(QR 1개). `headcount` 는 티켓 수가 아니라 이 티켓 1건이 대응하는 **입장 인원(N명분)** 이며, 티켓에 저장돼 현장 체크인 인원 판단에 쓰인다.
**멱등**: 같은 `reservationId` 로 다시 호출해도 중복 발급하지 않고 기존 티켓을 반환한다.

Request
```json
{
  "reservationId": 123,
  "expoId": 10,
  "roundId": 45,
  "userId": 77,
  "headcount": 3
}
```

Response `201 Created` (raw, 봉투 없음)
```json
{
  "ticketId": 1,
  "checkinToken": "ad12...",
  "issuedAt": "2026-09-07T02:00:00Z"
}
```

- `headcount` 는 1 이상. 아니면 `400 INVALID_REQUEST`.
- **멱등의 근거는 `tickets.reservation_id` 의 UNIQUE 제약**(마이그레이션 V2)이다. 예약당 1행이므로 유일하며, 이미 있으면 새로 만들지 않고 기존 티켓을 반환한다. 발급 통지가 fail-open(재시도 전제)이라, 동시 재호출로 UNIQUE 위반이 나도 기존 티켓을 반환한다.
- `checkinToken` 은 티켓당 1개, 현재 불투명 토큰(서버 조회 방식). 서명 토큰 전환 여부는 #74 에서 결정.

## 2. 티켓 무효화 — 예약 취소 시

`PATCH /internal/v1/tickets/reservation/{reservationId}/revoke`

예약이 취소되면 A가 호출한다. 해당 예약의 티켓을 `CANCELLED` 로 전이한다.
**멱등**: 발급 전이거나 이미 취소됐어도 성공. 이미 `USED`(체크인 완료)된 티켓은 건드리지 않는다.

Response `204 No Content` (본문 없음)

## 3. 티켓 조회 — 예약 상세 화면 (#82)

`GET /internal/v1/tickets/reservation/{reservationId}`

A가 "내 예약 상세"에서 QR을 표시하기 위해 호출한다. 예약당 티켓 1건이므로 단건 반환(벌크 없음).
목록 조회(`GET /api/v1/reservations/me`)에는 QR이 없으므로 이 단건 조회만 있으면 된다.

Response `200 OK` (raw, 봉투 없음)
```json
{
  "ticketId": 1,
  "checkinToken": "ad12...",
  "issuedAt": "2026-09-07T02:00:00Z",
  "status": "ISSUED"
}
```

- **`status` 필수** — 화면이 이걸로 분기한다: `ISSUED`→QR 표시, `USED`→"입장 완료", `CANCELLED`→무효 표시. `checkinToken` 만으로는 구분 불가.
- **`404` 는 정상 상황** — 발급 통지가 fail-open(재시도 대기)이라, 예약은 `CONFIRMED` 인데 티켓이 아직 없을 수 있다. 이때 500·빈 객체가 아니라 **404** 로 응답한다. A는 이를 `ticketAvailable: false`("티켓 발급 중")로 변환하며, 이렇게 해야 진짜 장애(503·타임아웃)와 구분된다.

## 열린 질문 (합의 필요)
1. 발급 실패 시 A의 처리 — 예약 확정은 됐는데 티켓 발급 호출이 실패하면? (서비스경계 문서: "재시도 큐 적재 + 사용자에게 '티켓 발급 중' 표시 후 폴링")
2. `headcount` 상한 — 회차 정원 검증은 A 책임으로 가정(티켓은 A가 넘긴 수를 그대로 발급).
3. ~~`checkinToken` 서명 방식(JWT형 vs 불투명+서버조회)~~ — **확정: 불투명 토큰 + 서버 조회**(#74). 랜덤 토큰이 UNIQUE·1회용이라 위조 불가, 서명 불필요.
