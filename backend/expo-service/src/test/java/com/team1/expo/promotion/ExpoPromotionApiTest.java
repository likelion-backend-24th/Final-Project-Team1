package com.team1.expo.promotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.team1.expo.domain.promotion.ExpoPromotion;
import com.team1.expo.domain.promotion.ExpoPromotionRepository;
import com.team1.expo.domain.promotion.ExpoPromotionStatus;
import com.team1.expo.domain.promotion.ExpoPaymentTransactionRepository;
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

class ExpoPromotionApiTest extends ApiTestSupport {

    @Autowired
    private ExpoPromotionRepository promotionRepository;
    @Autowired
    private ExpoPaymentTransactionRepository paymentTransactionRepository;

    private long ownerId;
    private String ownerToken;
    private long expoId;

    @BeforeEach
    void setUp() {
        ownerId = uniqueUserId();
        ownerToken = jwtFor(ownerId, "ORGANIZER");

        // 채널 생성
        ResponseEntity<JsonNode> channelRes = post("/api/v1/channels",
                """
                {"name":"%s","description":"배너 테스트 채널"}
                """.formatted(uniqueName()), ownerToken);
        long channelId = channelRes.getBody().path("data").path("id").asLong();

        // 박람회 생성
        ResponseEntity<JsonNode> expoRes = post("/api/v1/channels/" + channelId + "/expos",
                """
                {"title":"배너 테스트 박람회","category":"IT·전자","description":"설명","venue":"코엑스","region":"서울"}
                """, ownerToken);
        expoId = expoRes.getBody().path("data").path("id").asLong();
    }

    // ──────────────────────────────────────────────────────────
    // 배너 신청
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("ORGANIZER가 본인 박람회에 배너를 신청하면 201과 paymentId를 반환한다")
    void 배너_신청_성공() {
        ResponseEntity<JsonNode> response = post("/api/v1/expo-promotions",
                """
                {"expoId":%d}
                """.formatted(expoId), ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        assertThat(response.getBody().path("data").path("promotionId").asLong()).isPositive();
        assertThat(response.getBody().path("data").path("paymentId").asText()).startsWith("BE24-01-");
        assertThat(response.getBody().path("data").path("amount").asInt()).isEqualTo(9_900);
        assertThat(response.getBody().path("data").path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("이미 PENDING인 배너가 있으면 기존 PENDING을 취소하고 새로 신청된다")
    void 배너_PENDING_중복_시_자동정리_후_재신청() {
        ResponseEntity<JsonNode> first = post("/api/v1/expo-promotions", """
                {"expoId":%d}
                """.formatted(expoId), ownerToken);
        String firstPaymentId = first.getBody().path("data").path("paymentId").asText();

        ResponseEntity<JsonNode> response = post("/api/v1/expo-promotions",
                """
                {"expoId":%d}
                """.formatted(expoId), ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(response.getBody().path("data").path("paymentId").asText()).isNotEqualTo(firstPaymentId);
    }

    @Test
    @DisplayName("타인의 박람회에 배너를 신청하면 403을 반환한다")
    void 타인_박람회_배너_신청_거절() {
        String otherToken = jwtFor(uniqueUserId(), "ORGANIZER");

        ResponseEntity<JsonNode> response = post("/api/v1/expo-promotions",
                """
                {"expoId":%d}
                """.formatted(expoId), otherToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("존재하지 않는 expoId로 신청하면 404를 반환한다")
    void 없는_박람회_배너_신청_거절() {
        ResponseEntity<JsonNode> response = post("/api/v1/expo-promotions",
                """
                {"expoId":99999}
                """, ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("JWT 없이 배너를 신청하면 401을 반환한다")
    void 인증_없이_배너_신청_거절() {
        ResponseEntity<JsonNode> response = post("/api/v1/expo-promotions",
                """
                {"expoId":%d}
                """.formatted(expoId), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("USER role이 배너를 신청하면 403을 반환한다")
    void USER_역할_배너_신청_거절() {
        String userToken = jwtFor(ownerId, "USER");

        ResponseEntity<JsonNode> response = post("/api/v1/expo-promotions",
                """
                {"expoId":%d}
                """.formatted(expoId), userToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ──────────────────────────────────────────────────────────
    // 환불
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("ACTIVE 배너를 환불하면 200이 반환되고 CANCELLED로 전이된다")
    void 배너_환불_성공() {
        long promotionId = activePromotion();

        ResponseEntity<JsonNode> response = post(
                "/api/v1/expo-promotions/" + promotionId + "/refund", "", ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(promotionRepository.findById(promotionId).orElseThrow().getStatus())
                .isEqualTo(ExpoPromotionStatus.CANCELLED);
    }

    @Test
    @DisplayName("PENDING 상태 배너를 환불하면 409 INVALID_STATE_TRANSITION을 반환한다")
    void PENDING_배너_환불_거절() {
        // 배너 신청 → PENDING 상태
        ResponseEntity<JsonNode> applyRes = post("/api/v1/expo-promotions",
                """
                {"expoId":%d}
                """.formatted(expoId), ownerToken);
        long promotionId = applyRes.getBody().path("data").path("promotionId").asLong();

        ResponseEntity<JsonNode> response = post(
                "/api/v1/expo-promotions/" + promotionId + "/refund", "", ownerToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(errorCode(response)).isEqualTo("INVALID_STATE_TRANSITION");
    }

    @Test
    @DisplayName("타인의 배너를 환불하면 403을 반환한다")
    void 타인_배너_환불_거절() {
        long promotionId = activePromotion();
        String otherToken = jwtFor(uniqueUserId(), "ORGANIZER");

        ResponseEntity<JsonNode> response = post(
                "/api/v1/expo-promotions/" + promotionId + "/refund", "", otherToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ──────────────────────────────────────────────────────────
    // 웹훅
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("웹훅 paid 수신 시 ExpoPromotion이 ACTIVE로 전이된다")
    void 웹훅_paid_ACTIVE_전이() {
        ResponseEntity<JsonNode> applyRes = post("/api/v1/expo-promotions",
                """
                {"expoId":%d}
                """.formatted(expoId), ownerToken);
        long promotionId = applyRes.getBody().path("data").path("promotionId").asLong();
        String paymentId = applyRes.getBody().path("data").path("paymentId").asText();

        ResponseEntity<JsonNode> response = post("/api/v1/expo-promotions/webhooks/portone",
                """
                {"webhook_id":"wh-%s","payment_id":"%s","status":"paid"}
                """.formatted(paymentId, paymentId), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(promotionRepository.findById(promotionId).orElseThrow().getStatus())
                .isEqualTo(ExpoPromotionStatus.ACTIVE);
    }

    @Test
    @DisplayName("동일 webhook_id를 두 번 수신하면 200 멱등 응답하고 상태가 중복 변경되지 않는다")
    void 웹훅_중복_멱등() {
        ResponseEntity<JsonNode> applyRes = post("/api/v1/expo-promotions",
                """
                {"expoId":%d}
                """.formatted(expoId), ownerToken);
        String paymentId = applyRes.getBody().path("data").path("paymentId").asText();
        String webhookBody = """
                {"webhook_id":"wh-dup-%s","payment_id":"%s","status":"paid"}
                """.formatted(paymentId, paymentId);

        post("/api/v1/expo-promotions/webhooks/portone", webhookBody, null);
        ResponseEntity<JsonNode> second = post("/api/v1/expo-promotions/webhooks/portone", webhookBody, null);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("알 수 없는 paymentId 웹훅은 200으로 무시된다")
    void 웹훅_알수없는_paymentId_무시() {
        ResponseEntity<JsonNode> response = post("/api/v1/expo-promotions/webhooks/portone",
                """
                {"webhook_id":"wh-unknown-123","payment_id":"BE24-01-NOTEXIST","status":"paid"}
                """, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ──────────────────────────────────────────────────────────
    // 헬퍼
    // ──────────────────────────────────────────────────────────

    /** DB에 ACTIVE 상태의 ExpoPromotion + PaymentTransaction을 직접 생성한다. */
    private long activePromotion() {
        ExpoPromotion promotion = ExpoPromotion.create(expoId, 9_900, Clock.systemUTC());
        promotion.confirm(Clock.systemUTC());
        promotionRepository.save(promotion);

        paymentTransactionRepository.save(
                PaymentTransaction.create(promotion.getId(), "BE24-01-TEST" + promotion.getId(), 9_900, Instant.now()));

        return promotion.getId();
    }
}
