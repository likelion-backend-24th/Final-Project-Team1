package com.team1.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@AutoConfiguration
public class GeminiConfig {

    /** 설정이 비었을 때 쓰는 모델. 별칭이라 항상 최신 Flash 를 가리킨다(그만큼 붐빈다). */
    private static final String DEFAULT_MODEL = "gemini-flash-latest";

    /** 하루 호출 상한. 무료 한도(1,500건)를 한 기능이 다 쓰지 않게 막는다. 0 이면 무제한. */
    @Bean
    @ConditionalOnMissingBean
    public AiCallBudget aiCallBudget(@Value("${gemini.daily-call-limit:1000}") int dailyCallLimit) {
        return new AiCallBudget(dailyCallLimit, Clock.systemUTC());
    }

    /** 같은 프롬프트를 다시 물어보지 않는다. 한도와 응답 속도 둘 다에 이롭다. */
    @Bean
    @ConditionalOnMissingBean
    public AiResponseCache aiResponseCache(@Value("${gemini.cache-size:500}") int cacheSize,
                                           @Value("${gemini.cache-ttl:6h}") Duration cacheTtl) {
        return new AiResponseCache(cacheSize, cacheTtl, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public GeminiClient geminiClient(
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${gemini.api-key:}") String apiKey,
            // 모델명은 자주 바뀐다(gemini-1.5-flash 는 지원 종료됐다). 설정으로 둬야 배포 없이 바꾼다.
            @Value("${gemini.model:}") String model,
            @Value("${gemini.connect-timeout:3s}") Duration connectTimeout,
            @Value("${gemini.read-timeout:10s}") Duration readTimeout,
            // 5xx·타임아웃·429 만 재시도한다. 나머지 4xx 는 다시 보내도 같은 답이다.
            @Value("${gemini.max-attempts:2}") int maxAttempts,
            // 재시도 전 대기. 503 을 받고 바로 다시 보내면 429 를 우리가 만든다.
            @Value("${gemini.retry-delay:1s}") Duration retryDelay,
            ObjectMapper objectMapper,
            AiCallBudget budget,
            AiResponseCache cache) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();

        // 빈 값을 그대로 쓰면 경로가 /v1beta/models/:generateContent 가 되어 전부 404 다.
        // 환경변수는 "없음" 과 "빈 문자열" 이 구분되지 않으므로(.env 에 GEMINI_MODEL= 만 있어도
        // 빈 값이 온다) @Value 기본값에 기대지 않고 여기서 막는다.
        String modelName = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        return new GeminiClient(restClient, apiKey,
                "/v1beta/models/" + modelName + ":generateContent", objectMapper,
                budget, cache, maxAttempts, retryDelay);
    }

    @Bean
    @ConditionalOnMissingBean
    public AfterCommitRunner afterCommitRunner() {
        return new AfterCommitRunner();
    }

    /** LLM 비동기 작업 전용 풀 — ForkJoinPool 오염 방지 */
    @Bean("aiTaskExecutor")
    @ConditionalOnMissingBean(name = "aiTaskExecutor")
    public ExecutorService aiTaskExecutor(
            @Value("${ai.task-executor.threads:4}") int threads) {
        return Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "ai-task");
            t.setDaemon(true);
            return t;
        });
    }
}
