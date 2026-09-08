package com.team1.reservation.reservation;

import com.team1.reservation.reservation.service.Base32ReservationNoGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationNoGeneratorTest {

    private final Base32ReservationNoGenerator generator = new Base32ReservationNoGenerator();

    @Test
    @DisplayName("R-XXXX-XXXX 형식이고 uk 제약의 VARCHAR(20) 안에 들어간다")
    void hasExpectedFormat() {
        String no = generator.generate();

        assertThat(no).matches("^R-[0-9A-Z]{4}-[0-9A-Z]{4}$");
        assertThat(no).hasSize(11);
    }

    @Test
    @DisplayName("창구에서 혼동되는 I·L·O·U 를 쓰지 않는다")
    void avoidsAmbiguousLetters() {
        for (int i = 0; i < 500; i++) {
            assertThat(generator.generate().substring(2)).doesNotContainAnyWhitespaces()
                    .doesNotContain("I", "L", "O", "U");
        }
    }

    @Test
    @DisplayName("연속 생성해도 값이 겹치지 않는다")
    void generatesDistinctValues() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            generated.add(generator.generate());
        }

        assertThat(generated).hasSize(1000);
    }
}
