# 계약 1 — 예약 ↔ 티켓 (Reservation ⇄ Ticket) 내부 API

> Sprint 2. **A(Reservation-Service)** 가 호출하고 **C(Ticket-Service)** 가 제공한다.
> 서비스간 호출: `Authorization: Bearer <internal token>` + `X-Trace-Id` 헤더.
> 내부 경로는 **`/internal/v1/...`** 로 통일한다(API 계약 확정사항 #2·#9). nginx가 `/internal/**` 전체를 외부에서 차단한다.
> 정책: **QR 1개 = 티켓 1개 = 입장 1건.** 예약 인원(headcount) N → 티켓 N행.

## 1. 티켓 발급 — 예약 확정 시

`POST /internal/v1/tickets`

예약이 결제 확정되면 A가 호출한다. `headcount` 인원수만큼 티켓을 발급한다(티켓당 QR 1개).
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

Response `201 Created`
```json
{
  "success": true,
  "data": {
    "reservationId": 123,
    "tickets": [
      { "ticketId": 1, "status": "ISSUED", "checkinToken": "ad12...", "issuedAt": "2026-09-07T02:00:00Z" },
      { "ticketId": 2, "status": "ISSUED", "checkinToken": "cd2e...", "issuedAt": "2026-09-07T02:00:00Z" },
      { "ticketId": 3, "status": "ISSUED", "checkinToken": "5f5b...", "issuedAt": "2026-09-07T02:00:00Z" }
    ]
  },
  "meta": null,
  "message": null
}
```

- `headcount` 는 1 이상. 아니면 `400 INVALID_REQUEST`.
- **멱등은 reservation_id에 UNIQUE 제약을 걸지 않는다** — 예약당 티켓 N행이므로 reservation_id는 유일할 수 없다. "이미 발급된 티켓이 있으면 반환" 방식으로 보장한다.
- `checkinToken` 은 티켓당 1개, 현재 불투명 토큰(서버 조회 방식). 서명 토큰 전환 여부는 #74 에서 결정.

## 2. 티켓 무효화 — 예약 취소 시

`PATCH /internal/v1/tickets/reservation/{reservationId}/revoke`

예약이 취소되면 A가 호출한다. 해당 예약의 티켓을 `CANCELLED` 로 전이한다.
**멱등**: 발급 전이거나 이미 취소됐어도 성공. 이미 `USED`(체크인 완료)된 티켓은 건드리지 않는다.

Response `204 No Content` (본문 없음)

## 열린 질문 (합의 필요)
1. 발급 실패 시 A의 처리 — 예약 확정은 됐는데 티켓 발급 호출이 실패하면? (서비스경계 문서: "재시도 큐 적재 + 사용자에게 '티켓 발급 중' 표시 후 폴링")
2. `headcount` 상한 — 회차 정원 검증은 A 책임으로 가정(티켓은 A가 넘긴 수를 그대로 발급).
3. `checkinToken` 서명 방식(JWT형 vs 불투명+서버조회) — #74 에서 확정.
