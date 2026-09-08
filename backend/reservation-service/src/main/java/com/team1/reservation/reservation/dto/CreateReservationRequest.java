package com.team1.reservation.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateReservationRequest(

        @NotNull @Min(1) Integer headcount,

        @NotBlank @Size(max = 100) String contactName,

        /*
         * 입력은 하이픈이 있어도 없어도 받는다 - 사용자에게 표기 형식을 강요하지 않는다.
         * 저장은 normalizedPhone() 으로 숫자만 남겨 검색이 표기 형식을 타지 않게 한다.
         */
        @NotBlank
        @Pattern(regexp = "^01[0-9][- ]?\\d{3,4}[- ]?\\d{4}$", message = "휴대폰 번호 형식이 아닙니다")
        String contactPhone) {

    public String normalizedName() {
        return contactName == null ? null : contactName.trim();
    }

    public String normalizedPhone() {
        return contactPhone == null ? null : contactPhone.replaceAll("\\D", "");
    }

    /**
     * 예약자 이름·연락처를 제외한다. Validation 실패나 요청 Log 에 개인정보가 남지 않게 하기
     * 위해서이며, Sprint 1 의 {@code LoginRequest}·{@code SignUpRequest} 와 같은 방식이다.
     */
    @Override
    public String toString() {
        return "CreateReservationRequest{headcount=" + headcount + '}';
    }
}
