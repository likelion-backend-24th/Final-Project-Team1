package com.team1.reservation.calendar.dto;

public record ScheduleConstraint(Integer mainStartHourKst, String category) {
    public static final ScheduleConstraint NONE = new ScheduleConstraint(null, null);
}
