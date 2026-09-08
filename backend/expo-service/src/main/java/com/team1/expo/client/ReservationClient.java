package com.team1.expo.client;

import java.util.List;

public interface ReservationClient {

    /** GET /internal/v1/reservations/summary?expoId= */
    List<ReservationSummaryItem> getSummary(Long expoId);

    /** GET /internal/v1/reservations/attendees?expoId=&roundId= */
    List<AttendeeItem> getAttendees(Long expoId, Long roundId);

    record ReservationSummaryItem(Long roundId, int capacity, int confirmed, int cancelled) {}

    record AttendeeItem(
            Long reservationId,
            String reservationNo,
            Long roundId,
            String contactName,
            String contactPhone,
            int headcount,
            int amount,
            String status,
            String createdAt
    ) {}
}
