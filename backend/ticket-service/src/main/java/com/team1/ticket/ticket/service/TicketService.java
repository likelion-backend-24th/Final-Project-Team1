package com.team1.ticket.ticket.service;

import com.team1.ticket.common.ApiException;
import com.team1.ticket.common.ErrorCode;
import com.team1.ticket.ticket.dto.CheckinSummaryItem;
import com.team1.ticket.ticket.dto.IssueTicketsRequest;
import com.team1.ticket.ticket.dto.IssuedTicketResponse;
import com.team1.ticket.ticket.dto.TicketDetailResponse;
import com.team1.ticket.ticket.entity.Ticket;
import com.team1.ticket.ticket.entity.TicketStatus;
import com.team1.ticket.ticket.repository.TicketRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;


@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final Clock clock;

    public TicketService(TicketRepository ticketRepository, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.clock = clock;
    }

    // 예약 확정 시 티켓 1건 발급(예약당 1건, API 계약 v3 #17). 코드는 1개지만 headcount 명분이다.
    // 멱등: 같은 예약으로 이미 발급된 티켓이 있으면 재발급하지 않고 기존 것을 반환한다.
    // 발급 통지가 fail-open(재시도 전제)이라, 동시 재호출로 UNIQUE 위반이 나도 기존 티켓을 반환한다.
    //
    // 일부러 @Transactional 을 걸지 않는다. 걸면 save() 가 이 메서드의 트랜잭션에 참여하고,
    // UNIQUE 위반 시 세션이 오염되고 트랜잭션이 rollback-only 로 표시돼 catch 안의 재조회·커밋이
    // UnexpectedRollbackException 으로 터진다. 트랜잭션 경계를 save()(레포 메서드) 단위로 두면
    // 실패한 INSERT 트랜잭션만 롤백되고, 뒤이은 재조회는 깨끗한 새 트랜잭션에서 커밋된 승자 행을 본다.
    public IssuedTicketResponse issue(IssueTicketsRequest request) {
        return ticketRepository.findByReservationId(request.reservationId())
                .map(IssuedTicketResponse::from)
                .orElseGet(() -> create(request));
    }

    // save() 가 자체 트랜잭션(SimpleJpaRepository)이라 여기서 트랜잭션을 열지 않는다.
    private IssuedTicketResponse create(IssueTicketsRequest request) {
        Ticket ticket = Ticket.issue(
                request.reservationId(),
                request.expoId(),
                request.roundId(),
                request.userId(),
                request.headcount(),
                newToken(),
                clock.instant());
        try {
            return IssuedTicketResponse.from(ticketRepository.save(ticket));
        } catch (DataIntegrityViolationException raced) {
            // 동시 재시도가 reservation_id UNIQUE 를 동시에 뚫으려다 진 경우 → 기존 티켓 반환(멱등).
            // 승자 트랜잭션이 커밋된 뒤라야 아래 재조회가 그 행을 본다.
            return ticketRepository.findByReservationId(request.reservationId())
                    .map(IssuedTicketResponse::from)
                    .orElseThrow(() -> raced);
        }
    }

    // 예약별 티켓 단건 조회. 예약 상세 화면(QR 표시)이 호출한다.
    // 티켓이 아직 없으면(발급 통지 실패로 재시도 대기 중 등) 404 — 예약측이 ticketAvailable=false 로
    // "발급 중"을 표시할 수 있게 한다. 빈 객체·500 이 아니라 404 여야 진짜 장애(503)와 구분된다.
    @Transactional(readOnly = true)
    public TicketDetailResponse getByReservation(Long reservationId) {
        return ticketRepository.findByReservationId(reservationId)
                .map(TicketDetailResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "ticket not found for reservation: " + reservationId));
    }

    // 체크인 현황 집계(#121, Story 8). 박람회-Service 가 예약 현황 화면에 합치려고 당겨간다.
    // 회차별 체크인 완료 인원(USED 티켓 headcount 합). 체크인 0인 회차는 목록에 안 나온다.
    @Transactional(readOnly = true)
    public List<CheckinSummaryItem> getCheckinSummary(Long expoId) {
        return ticketRepository.sumCheckedInByRound(expoId, TicketStatus.USED);
    }

    // 예약 취소 통지 → 해당 예약의 티켓 무효화. 이미 사용(USED)된 티켓은 건드리지 않는다.
    // 발급 전이거나 이미 취소됐어도 멱등적으로 성공한다.
    @Transactional
    public void revokeByReservation(Long reservationId) {
        ticketRepository.findByReservationId(reservationId)
                .filter(ticket -> ticket.getStatus() == TicketStatus.ISSUED)
                .ifPresent(Ticket::cancel);
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
