package com.team1.ticket.ticket.service;

import com.team1.ticket.client.ExpoClient;
import com.team1.ticket.client.ExpoSummary;
import com.team1.ticket.common.ApiException;
import com.team1.ticket.common.ErrorCode;
import com.team1.ticket.ticket.dto.CheckinResult;
import com.team1.ticket.ticket.dto.CheckinTicketView;
import com.team1.ticket.ticket.entity.Ticket;
import com.team1.ticket.ticket.repository.TicketRepository;
import com.team1.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;


// 현장 체크인(Story 7, #74). 주최자가 브라우저에서 호출하는 외부 API 를 서빙한다.
// 흐름: verify(스캔→조회, 미전이) → checkin(확정→USED). 둘 다 박람회 소유권을 검증한다.
// 조회 수단은 QR 의 체크인 토큰과 예약번호 두 가지지만, 확정은 checkin() 하나로 모인다.
@Service
public class TicketCheckinService {

    private static final String ROLE_ORGANIZER = "ORGANIZER";

    private final TicketRepository ticketRepository;
    private final ExpoClient expoClient;
    private final Clock clock;

    public TicketCheckinService(TicketRepository ticketRepository, ExpoClient expoClient, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.expoClient = expoClient;
        this.clock = clock;
    }

    // 체크인 토큰 또는 예약번호로 티켓을 조회한다. 상태를 바꾸지 않는다(주최자가 확정 전에 확인).
    @Transactional(readOnly = true)
    public CheckinTicketView verify(String code, String reservationNo, AuthenticatedUser organizer) {
        Ticket ticket = findTicket(code, reservationNo);
        verifyOwnership(ticket, organizer);
        return CheckinTicketView.from(ticket);
    }

    // 체크인 확정. ISSUED → USED (1회용). 이미 사용/취소면 거부.
    @Transactional
    public CheckinResult checkin(Long ticketId, AuthenticatedUser organizer) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "ticket not found: " + ticketId));
        verifyOwnership(ticket, organizer);
        ticket.checkIn(clock.instant());
        return CheckinResult.from(ticket);
    }

    private Ticket findTicket(String code, String reservationNo) {
        boolean hasCode = hasText(code);
        boolean hasReservationNo = hasText(reservationNo);
        // 둘 다 받으면 어느 쪽을 믿을지가 애매해지고, 서로 다른 티켓을 가리킬 수도 있다.
        if (hasCode == hasReservationNo) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "exactly one of code or reservationNo is required");
        }
        Optional<Ticket> found = hasCode
                ? ticketRepository.findByCheckinToken(code.trim())
                : ticketRepository.findByReservationNo(reservationNo.trim().toUpperCase(Locale.ROOT));
        // 예약번호의 존재 여부를 흘리지 않으려고 토큰 경로와 같은 메시지를 쓴다.
        return found.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "invalid ticket"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    // 주최자만, 그리고 그 티켓 박람회의 소유자만 체크인할 수 있다.
    // 소유권은 박람회-Service 만 알고 있어 getExpoInternal 로 확인한다(#7). 실패는 fail-closed.
    private void verifyOwnership(Ticket ticket, AuthenticatedUser organizer) {
        if (organizer == null || !ROLE_ORGANIZER.equals(organizer.role())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "organizer role required");
        }
        ExpoSummary expo = expoClient.getExpo(ticket.getExpoId());
        if (!expo.channelOwnerId().equals(organizer.userId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "not the owner of this expo");
        }
    }
}
