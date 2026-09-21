package com.team1.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 네트워크 없이 도는 테스트. 실제 Gemini 는 부르지 않는다. */
class GeminiClientTest {

    private static final Instant NOW = Instant.parse("2026-09-18T02:00:00Z");
    private static final String MODEL_PATH = "/v1beta/models/gemini-flash-latest:generateContent";
    private static final String FEATURE = "expo-tagging";
    private static final String API_KEY = "test-key";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockRestServiceServer server;
    private RestClient restClient;
    private AiCallBudget budget;
    private AiResponseCache cache;

    /** Gemini 응답 봉투. 본문 텍스트는 문자열로 한 번 더 감싸여 온다. */
    private static String geminiBody(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + MAPPER.valueToTree(text) + "}]}}]}";
    }

    public record Keywords(List<String> keywords) {
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        budget = new AiCallBudget(1000, clock);
        cache = new AiResponseCache(500, Duration.ofHours(6), clock);
    }

    private GeminiClient client(String apiKey, int maxAttempts) {
        // 테스트에서는 대기 없이 재시도한다. 여기서 기다려 봐야 느려지기만 한다.
        return new GeminiClient(restClient, apiKey, MODEL_PATH, MAPPER, budget, cache,
                maxAttempts, Duration.ZERO, "", -1);
    }

    private GeminiClient client() {
        return client(API_KEY, 2);
    }

    @Test
    @DisplayName("키는 헤더로만 보낸다 - URL 에 실으면 로그·프록시에 남는다")
    void sendsApiKeyInHeader() {
        server.expect(once(), requestTo(containsString(MODEL_PATH)))
                .andExpect(method(POST))
                .andExpect(header("x-goog-api-key", API_KEY))
                .andRespond(withSuccess(geminiBody("안녕하세요"), MediaType.APPLICATION_JSON));

        assertThat(client().generateText(FEATURE, "인사해줘")).isEqualTo("안녕하세요");
        server.verify();
    }

    @Test
    @DisplayName("JSON 응답을 타입으로 바꿔 준다 - 기능마다 파싱 코드를 두지 않는다")
    void parsesJsonIntoType() {
        server.expect(once(), requestTo(containsString(MODEL_PATH)))
                .andRespond(withSuccess(geminiBody("{\"keywords\":[\"AI\",\"로봇\"]}"), MediaType.APPLICATION_JSON));

        Keywords result = client().generateJson(FEATURE, "키워드 뽑아줘", Keywords.class);

        assertThat(result).isNotNull();
        assertThat(result.keywords()).containsExactly("AI", "로봇");
    }

    @Test
    @DisplayName("모델이 JSON 형식을 어기면 null - 호출부는 폴백으로 간다")
    void returnsNullWhenJsonIsBroken() {
        server.expect(once(), requestTo(containsString(MODEL_PATH)))
                .andRespond(withSuccess(geminiBody("키워드는 AI 입니다"), MediaType.APPLICATION_JSON));

        assertThat(client().generateJson(FEATURE, "키워드", Keywords.class)).isNull();
    }

    @Test
    @DisplayName("같은 프롬프트는 캐시로 답한다 - 호출이 한 번만 나간다")
    void usesCacheForSamePrompt() {
        server.expect(once(), requestTo(containsString(MODEL_PATH)))
                .andRespond(withSuccess(geminiBody("한 번만"), MediaType.APPLICATION_JSON));

        GeminiClient client = client();
        assertThat(client.generateText(FEATURE, "같은 질문")).isEqualTo("한 번만");
        assertThat(client.generateText(FEATURE, "같은 질문")).isEqualTo("한 번만");

        server.verify(); // 두 번 나갔으면 once() 기대가 깨진다
    }

    @Test
    @DisplayName("5xx 는 재시도한다")
    void retriesOnServerError() {
        server.expect(once(), requestTo(containsString(MODEL_PATH))).andRespond(withServerError());
        server.expect(once(), requestTo(containsString(MODEL_PATH)))
                .andRespond(withSuccess(geminiBody("두 번째에 성공"), MediaType.APPLICATION_JSON));

        assertThat(client().generateText(FEATURE, "재시도")).isEqualTo("두 번째에 성공");
        server.verify();
    }

    @Test
    @DisplayName("429 는 재시도한다 - 한도는 시간이 지나면 풀린다")
    void retriesOnTooManyRequests() {
        server.expect(once(), requestTo(containsString(MODEL_PATH))).andRespond(withStatus(TOO_MANY_REQUESTS));
        server.expect(once(), requestTo(containsString(MODEL_PATH)))
                .andRespond(withSuccess(geminiBody("한도가 풀린 뒤 성공"), MediaType.APPLICATION_JSON));

        assertThat(client().generateText(FEATURE, "한도")).isEqualTo("한도가 풀린 뒤 성공");
        server.verify();
    }

    @Test
    @DisplayName("4xx 는 재시도하지 않는다 - 키·모델명 문제라 다시 보내도 같다")
    void doesNotRetryOnClientError() {
        server.expect(once(), requestTo(containsString(MODEL_PATH))).andRespond(withStatus(FORBIDDEN));

        assertThat(client().generateText(FEATURE, "권한없음")).isNull();
        server.verify();
    }

    @Test
    @DisplayName("안전 필터 등으로 본문이 비면 null")
    void returnsNullWhenNoText() {
        server.expect(once(), requestTo(containsString(MODEL_PATH)))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client().generateText(FEATURE, "빈응답")).isNull();
    }

    @Test
    @DisplayName("하루 상한을 넘기면 호출하지 않는다")
    void stopsWhenDailyLimitExceeded() {
        budget = new AiCallBudget(1, Clock.fixed(NOW, ZoneOffset.UTC));
        GeminiClient client = client();

        assertThat(budget.tryAcquire("other-feature")).isTrue();

        assertThat(client.isAvailable()).isFalse();
        assertThat(client.generateText(FEATURE, "상한초과")).isNull();
        server.verify(); // 호출이 아예 나가지 않아야 한다
    }

    @Test
    @DisplayName("기능별로 사용량을 센다 - 누가 한도를 쓰는지 보여야 한다")
    void countsUsagePerFeature() {
        budget.tryAcquire("expo-tagging");
        budget.tryAcquire("expo-tagging");
        budget.tryAcquire("notification-message");

        assertThat(budget.usage())
                .containsEntry("expo-tagging", 2)
                .containsEntry("notification-message", 1);
    }

    @Test
    @DisplayName("키가 없으면 호출하지 않는다 - 로컬·CI 에서 키 없이도 떠야 한다")
    void skipsWithoutApiKey() {
        GeminiClient client = client("", 2);

        assertThat(client.isAvailable()).isFalse();
        assertThat(client.generateText(FEATURE, "키없음")).isNull();
        server.verify();
    }

    @Test
    @DisplayName("캐시 TTL 이 0 이면 저장하지 않는다")
    void expiresCacheEntries() {
        AiResponseCache shortLived = new AiResponseCache(10, Duration.ofMinutes(5), Clock.systemUTC());
        shortLived.put("k", "v");
        assertThat(shortLived.get("k")).isEqualTo("v");

        // TTL 0 은 캐시를 쓰지 않겠다는 뜻이다
        AiResponseCache disabled = new AiResponseCache(10, Duration.ZERO, Clock.systemUTC());
        disabled.put("k", "v");
        assertThat(disabled.get("k")).isNull();
    }
}
