package com.team1.reservation.reservation.service;

import com.team1.reservation.client.RecommendationClient;
import com.team1.reservation.config.AfterCommitExecutor;
import com.team1.reservation.reservation.entity.Reservation;
import org.springframework.stereotype.Component;

/**
 * 예약 확정을 추천 서비스에 알린다(#184). 추천 점수와 "예약 확정" 알림(#231)의 입력이 된다.
 *
 * <p>티켓 통지와 달리 재시도 큐가 없다. 빠져도 예약·입장에는 영향이 없고 알림 하나가 덜 올 뿐이다.
 */
@Component
public class RecommendationEventNotifier {

    private final RecommendationClient client;
    private final AfterCommitExecutor afterCommit;

    public RecommendationEventNotifier(RecommendationClient client, AfterCommitExecutor afterCommit) {
        this.client = client;
        this.afterCommit = afterCommit;
    }

    public void reservationConfirmed(Reservation reservation) {
        // 커밋 뒤에는 엔티티가 준영속일 수 있으니 값을 먼저 꺼낸다
        Long userId = reservation.getUserId();
        Long expoId = reservation.getExpoId();
        Long reservationId = reservation.getId();
        String reservationNo = reservation.getReservationNo();

        // 롤백될 확정을 알리면 없는 예약의 알림이 생긴다. 커밋 뒤에만 보낸다.
        afterCommit.execute(() -> client.sendReservationConfirmed(userId, expoId, reservationId, reservationNo));
    }
}
