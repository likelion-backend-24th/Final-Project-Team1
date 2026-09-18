package com.team1.recommendation.expo.service;

import com.team1.ai.AfterCommitRunner;
import com.team1.ai.GeminiClient;
import com.team1.recommendation.expo.entity.ExpoTag;
import com.team1.recommendation.expo.repository.ExpoTagRepository;
import com.team1.recommendation.internal.dto.ExpoPublishedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Service
public class ExpoTagService {

    private static final Logger log = LoggerFactory.getLogger(ExpoTagService.class);
    private static final int MAX_TAG_LENGTH = 50;
    private static final int MAX_TAG_COUNT = 5;

    /** 호출량 집계·로그 단위. 어느 기능이 한도를 쓰는지 여기로 구분한다. */
    private static final String FEATURE = "expo-tagging";

    private final ExpoTagRepository repository;
    private final GeminiClient geminiClient;
    private final AfterCommitRunner afterCommitRunner;
    private final ExecutorService executor;

    public ExpoTagService(ExpoTagRepository repository,
                          GeminiClient geminiClient,
                          AfterCommitRunner afterCommitRunner,
                          @Qualifier("aiTaskExecutor") ExecutorService executor) {
        this.repository = repository;
        this.geminiClient = geminiClient;
        this.afterCommitRunner = afterCommitRunner;
        this.executor = executor;
    }

    /** LLM 응답을 받는 그릇. 파싱은 common-ai 가 한다. */
    public record Keywords(List<String> keywords) {}

    @Transactional
    public void handleExpoPublished(ExpoPublishedRequest request) {
        log.info("expo-published received expoId={}", request.expoId());
        boolean alreadyTagged = repository.findByExpoId(request.expoId()).stream()
                .anyMatch(t -> !"UNTAGGED".equals(t.getTagValue()));
        if (alreadyTagged) return;

        repository.deleteByExpoId(request.expoId());
        afterCommitRunner.execute(() -> executor.submit(() -> tagAsync(request)));
    }

    @Transactional
    public void retag(Long expoId, String title, String description) {
        repository.deleteByExpoId(expoId);
        executor.submit(() -> tagAsync(new ExpoPublishedRequest(expoId, title, description)));
    }

    private void tagAsync(ExpoPublishedRequest request) {
        try {
            // delimiter로 감싸서 프롬프트 인젝션 방어
            String prompt = """
                    다음 박람회 정보를 분석해 JSON으로만 응답하세요 (설명 없이):
                    [제목]%s[/제목]
                    [설명]%s[/설명]

                    응답 형식:
                    {"keywords":["키워드1","키워드2","키워드3","키워드4","키워드5"]}
                    """.formatted(
                    request.title() != null ? request.title() : "",
                    request.description() != null ? request.description() : "");

            Keywords parsed = geminiClient.generateJson(FEATURE, prompt, Keywords.class);
            List<String> keywords = validate(parsed != null ? parsed.keywords() : null);

            if (keywords.isEmpty()) {
                saveFallback(request.expoId());
                return;
            }

            saveKeywords(request.expoId(), keywords);
            log.info("Gemini tagging done expoId={} keywords={}", request.expoId(), keywords);

        } catch (Exception e) {
            log.warn("Gemini tagging failed expoId={}, using fallback", request.expoId(), e);
            saveFallback(request.expoId());
        }
    }

    /** trim · 소문자 · 중복제거 · 길이 자르기 · 개수 상한 */
    static List<String> validate(List<String> raw) {
        if (raw == null) return List.of();
        return raw.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> k.trim().toLowerCase())
                .map(k -> k.length() > MAX_TAG_LENGTH ? k.substring(0, MAX_TAG_LENGTH) : k)
                .distinct()
                .limit(MAX_TAG_COUNT)
                .toList();
    }

    private void saveKeywords(Long expoId, List<String> keywords) {
        LocalDateTime now = LocalDateTime.now();
        keywords.stream()
                .filter(kw -> kw != null && !kw.isBlank())
                .map(String::trim)
                .map(kw -> kw.length() > MAX_TAG_LENGTH ? kw.substring(0, MAX_TAG_LENGTH) : kw)
                .distinct()
                .limit(MAX_TAG_COUNT)
                .forEach(kw -> repository.save(ExpoTag.of(expoId, kw, null, now)));
    }

    private void saveFallback(Long expoId) {
        repository.save(ExpoTag.of(expoId, "UNTAGGED", null, LocalDateTime.now()));
    }
}
