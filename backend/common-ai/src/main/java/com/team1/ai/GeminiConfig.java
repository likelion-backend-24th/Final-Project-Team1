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
            @Value("${gemini.model:gemini-flash-latest}") String model,
            @Value("${gemini.connect-timeout:3s}") Duration connectTimeout,
            @Value("${gemini.read-timeout:10s}") Duration readTimeout,
            // 5xx·타임아웃만 재시도한다. 4xx 는 다시 보내도 같은 답이다.
            @Value("${gemini.max-attempts:2}") int maxAttempts,
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

        return new GeminiClient(restClient, apiKey,
                "/v1beta/models/" + model + ":generateContent", objectMapper,
                budget, cache, maxAttempts);
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
