package com.team1.expo.promotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.expo.domain.promotion.ExpoPromotion;
import com.team1.expo.domain.promotion.ExpoPromotionRepository;
import com.team1.expo.domain.promotion.PaymentTransactionRepository;
import com.team1.expo.support.ApiTestSupport;
import com.team1.payment.PaymentTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ActivePromotionApiTest extends ApiTestSupport {

    @Autowired
    private ExpoPromotionRepository promotionRepository;
    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    private long expoId1;
    private long expoId2;

    @BeforeEach
    void setUp() {
        expoId1 = createExpo();
        expoId2 = createExpo();
    }

    @Test
    @DisplayName("ACTIVE 배너가 없으면 빈 배열을 반환한다")
    void 활성_배너_없음() {
        ResponseEntity<JsonNode> response = get("/api/v1/expo-promotions/active", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("data").isArray()).isTrue();
    }

    @Test
    @DisplayName("ACTIVE 배너가 paidAt 오름차순으로 반환된다")
    void 활성_배너_순서_확인() {
        long p1 = activePromotion(expoId1, Instant.parse("2026-09-01T00:00:00Z"));
        long p2 = activePromotion(expoId2, Instant.parse("2026-09-02T00:00:00Z"));

        ResponseEntity<JsonNode> response = get("/api/v1/expo-promotions/active", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = response.getBody().path("data");
        // p1이 p2보다 앞에 와야 한다 (paidAt 오름차순)
        long firstId = data.get(0).path("promotionId").asLong();
        long secondId = data.get(1).path("promotionId").asLong();
        assertThat(firstId).isEqualTo(p1);
        assertThat(secondId).isEqualTo(p2);
    }

    @Test
    @DisplayName("PENDING/CANCELLED 배너는 조회에 포함되지 않는다")
    void 비활성_배너_미포함() {
        // PENDING 상태
        ExpoPromotion pending = ExpoPromotion.create(expoId1, 9_900, Clock.systemUTC());
        promotionRepository.save(pending);

        ResponseEntity<JsonNode> response = get("/api/v1/expo-promotions/active", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = response.getBody().path("data");
        assertThat(data.isArray()).isTrue();
        // pending은 포함되지 않음
        for (JsonNode node : data) {
            assertThat(node.path("promotionId").asLong()).isNotEqualTo(pending.getId());
        }
    }

    @Test
    @DisplayName("JWT 없이도 조회 가능하다 (공개 API)")
    void 인증_없이_조회_가능() {
        ResponseEntity<JsonNode> response = get("/api/v1/expo-promotions/active", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ──────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────

    private long createExpo() {
        long ownerId = uniqueUserId();
        String token = jwtFor(ownerId, "ORGANIZER");

        ResponseEntity<JsonNode> channelRes = post("/api/v1/channels",
                """
                {"name":"%s","description":"배너 조회 테스트"}
                """.formatted(uniqueName()), token);
        long channelId = channelRes.getBody().path("data").path("id").asLong();

        ResponseEntity<JsonNode> expoRes = post("/api/v1/channels/" + channelId + "/expos",
                """
                {"title":"배너 테스트 박람회","category":"IT·전자","description":"설명","venue":"코엑스","region":"서울"}
                """, token);
        return expoRes.getBody().path("data").path("id").asLong();
    }

    private long activePromotion(long expoId, Instant paidAt) {
        ExpoPromotion promotion = ExpoPromotion.create(expoId, 9_900, Clock.systemUTC());
        promotion.confirm(Clock.fixed(paidAt, java.time.ZoneOffset.UTC));
        promotionRepository.save(promotion);
        paymentTransactionRepository.save(
                PaymentTransaction.create(promotion.getId(), "BE24-D-" + promotion.getId(), 9_900, paidAt));
        return promotion.getId();
    }
}
