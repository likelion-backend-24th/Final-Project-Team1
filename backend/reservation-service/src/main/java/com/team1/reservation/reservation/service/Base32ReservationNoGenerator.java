package com.team1.reservation.reservation.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * {@code R-XXXX-XXXX} 형식의 예약번호를 만든다. 예: {@code R-4K7Q-W2M8}
 * <p>Crockford Base32 를 쓴다 — 숫자 10개에 알파벳 22개로, 헷갈리는 네 글자
 * {@code I}·{@code L}·{@code O}·{@code U} 를 뺐다. 창구에서 예약번호를 불러주거나 받아 적을 때
 * {@code I} 와 {@code 1}, {@code O} 와 {@code 0} 을 혼동하는 일이 실제로 생기기 때문이다.
 * <p>날짜를 넣지 않았다. {@code R260908-XXXX} 처럼 날짜를 붙이면 창구에서 눈으로 훑기 편하다는
 * 장점이 있지만, 사람에게 보이는 값이라 KST 로 찍어야 하고 그러면 저장은 UTC 라는 팀 규칙과
 * 섞여 헷갈린다. 날짜가 필요하면 {@code created_at} 을 보면 된다.
 */
@Component
public class Base32ReservationNoGenerator implements ReservationNoGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int BODY_LENGTH = 8;

    private final RandomGenerator random;

    public Base32ReservationNoGenerator() {
        this(new SecureRandom());
    }

    Base32ReservationNoGenerator(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public String generate() {
        StringBuilder sb = new StringBuilder("R-");
        for (int i = 0; i < BODY_LENGTH; i++) {
            if (i == BODY_LENGTH / 2) {
                sb.append('-');
            }
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
