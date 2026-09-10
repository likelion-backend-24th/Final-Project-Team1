package com.team1.reservation.reservation;

import com.team1.reservation.client.IssueTicketCommand;
import com.team1.reservation.client.IssuedTicket;
import com.team1.reservation.client.TicketClient;
import com.team1.reservation.common.ApiException;
import com.team1.reservation.common.ErrorCode;
import com.team1.reservation.reservation.entity.TicketDispatch;
import com.team1.reservation.reservation.entity.TicketDispatchStatus;
import com.team1.reservation.reservation.repository.TicketDispatchRepository;
import com.team1.reservation.reservation.service.TicketDispatchService;
import com.team1.reservation.reservation.service.TicketDispatcher;
import com.team1.reservation.reservation.support.TicketDispatchStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** #79 재시도 큐. 통지가 실패해도 티켓이 영영 안 나오는 일이 없어야 한다. */
class TicketDispatchTest {

    private static final Instant NOW = Instant.parse("2026-09-10T04:00:00Z");
    private static final IssuedTicket TICKET = new IssuedTicket(900L, "chk-abc", NOW);

    private TicketDispatchRepository queue;
    private TicketClient ticketClient;
    private TicketDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        queue = TicketDispatchStub.queue();
        ticketClient = mock(TicketClient.class);
        dispatcher = TicketDispatchStub.dispatcher(queue, ticketClient, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private TicketDispatch enqueued() {
        return queue.save(TicketDispatch.pending(42L, 1L, 7L, 100L, 3, NOW));
    }

    @Test
    @DisplayName("성공하면 SUCCEEDED 로 닫고 ticketId 를 남긴다")
    void closesQueueEntryOnSuccess() {
        TicketDispatch dispatch = enqueued();
        when(ticketClient.issueTicket(any())).thenReturn(TICKET);

        assertThat(dispatcher.dispatch(dispatch.getId())).isTrue();

        assertThat(dispatch.getStatus()).isEqualTo(TicketDispatchStatus.SUCCEEDED);
        assertThat(dispatch.getTicketId()).isEqualTo(900L);
    }

    @Test
    @DisplayName("적재한 예약 정보를 그대로 보낸다")
    void sendsEnqueuedCommand() {
        TicketDispatch dispatch = enqueued();
        when(ticketClient.issueTicket(any())).thenReturn(TICKET);

        dispatcher.dispatch(dispatch.getId());

        verify(ticketClient).issueTicket(new IssueTicketCommand(42L, 1L, 7L, 100L, 3));
    }

    @Test
    @DisplayName("실패하면 PENDING 으로 남고 다음 시도 시각이 뒤로 밀린다")
    void reschedulesOnFailure() {
        TicketDispatch dispatch = enqueued();
        when(ticketClient.issueTicket(any()))
                .thenThrow(new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "ticket-service unavailable"));

        assertThat(dispatcher.dispatch(dispatch.getId())).isFalse();

        assertThat(dispatch.getStatus()).isEqualTo(TicketDispatchStatus.PENDING);
        assertThat(dispatch.getAttempts()).isEqualTo(1);
        assertThat(dispatch.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(dispatch.getLastError()).contains("ticket-service unavailable");
    }

    @Test
    @DisplayName("재시도 간격은 지수로 벌어진다 - 복구 중인 서비스를 같은 속도로 두드리지 않는다")
    void backsOffExponentially() {
        TicketDispatch dispatch = enqueued();
        when(ticketClient.issueTicket(any())).thenThrow(new IllegalStateException("boom"));

        dispatcher.dispatch(dispatch.getId());
        assertThat(dispatch.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));

        dispatcher.dispatch(dispatch.getId());
        assertThat(dispatch.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));

        dispatcher.dispatch(dispatch.getId());
        assertThat(dispatch.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(4)));
    }

    @Test
    @DisplayName("상한을 넘기면 GAVE_UP 으로 두고 더 시도하지 않는다")
    void givesUpAfterMaxAttempts() {
        TicketDispatch dispatch = enqueued();
        when(ticketClient.issueTicket(any())).thenThrow(new IllegalStateException("boom"));

        for (int i = 0; i < TicketDispatchStub.MAX_ATTEMPTS; i++) {
            dispatcher.dispatch(dispatch.getId());
        }
        assertThat(dispatch.getStatus()).isEqualTo(TicketDispatchStatus.GAVE_UP);

        // 포기한 뒤에는 아무리 불러도 나가지 않는다.
        dispatcher.dispatch(dispatch.getId());
        verify(ticketClient, times(TicketDispatchStub.MAX_ATTEMPTS)).issueTicket(any());
    }

    @Test
    @DisplayName("이미 성공한 통지는 다시 보내지 않는다 - 배치와 즉시 시도가 겹쳐도 1회다")
    void doesNotResendSucceeded() {
        TicketDispatch dispatch = enqueued();
        when(ticketClient.issueTicket(any())).thenReturn(TICKET);

        dispatcher.dispatch(dispatch.getId());
        dispatcher.dispatch(dispatch.getId());

        verify(ticketClient, times(1)).issueTicket(any());
    }

    @Test
    @DisplayName("배치는 시각이 된 건만 집어 보낸다")
    void batchDispatchesDueEntries() {
        TicketDispatch dispatch = enqueued();
        when(queue.findDue(any(), any())).thenReturn(List.of(dispatch));
        when(ticketClient.issueTicket(any())).thenReturn(TICKET);

        TicketDispatchService service =
                new TicketDispatchService(queue, dispatcher, Clock.fixed(NOW, ZoneOffset.UTC), 100);

        assertThat(service.dispatchDue()).isEqualTo(1);
        assertThat(dispatch.getStatus()).isEqualTo(TicketDispatchStatus.SUCCEEDED);
    }
}
