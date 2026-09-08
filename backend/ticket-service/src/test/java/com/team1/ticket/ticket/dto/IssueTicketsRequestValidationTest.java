package com.team1.ticket.ticket.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// headcount·필수값 검증은 컨트롤러 @Valid 로 강제된다(위반 시 400 INVALID_REQUEST).
// 여기서는 그 제약(@Min(1)·@NotNull)이 실제로 걸려 있는지 확인한다.
class IssueTicketsRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("정상 요청은 위반이 없다")
    void validRequestHasNoViolations() {
        IssueTicketsRequest request = new IssueTicketsRequest(123L, 10L, 45L, 77L, 3);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("headcount 가 1 미만이면 위반이 발생한다 (400 대상)")
    void rejectsHeadcountBelowOne() {
        IssueTicketsRequest request = new IssueTicketsRequest(123L, 10L, 45L, 77L, 0);

        assertThat(validator.validate(request))
                .anyMatch(v -> v.getPropertyPath().toString().equals("headcount"));
    }

    @Test
    @DisplayName("필수 식별자가 null 이면 위반이 발생한다")
    void rejectsNullReservationId() {
        IssueTicketsRequest request = new IssueTicketsRequest(null, 10L, 45L, 77L, 3);

        assertThat(validator.validate(request))
                .anyMatch(v -> v.getPropertyPath().toString().equals("reservationId"));
    }
}
