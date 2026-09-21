package com.team1.expo.expo.search;

import com.team1.ai.GeminiClient;
import com.team1.expo.domain.expo.ExpoCategories;
import com.team1.expo.expo.repository.ExpoQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 검색 문장을 검색 필터로 옮긴다. <b>LLM 이 하는 일은 여기까지다</b> - 박람회 목록은 뒤에서 DB 가 낸다.
 *
 * <p>모델이 무엇을 뱉든 지역·카테고리는 실제 존재하는 값에만 대응되고, 그 밖의 값은 조건에서 빠진다.
 * 그래서 없는 박람회가 결과에 섞일 경로가 없다.
 *
 * <p>해석에 실패하면 문장을 통째로 키워드로 넘긴다(fail-open). LLM 때문에 검색이 막히면 안 된다.
 */
@Service
public class SearchQueryParser {

    private static final Logger log = LoggerFactory.getLogger(SearchQueryParser.class);

    /** 호출량 집계·로그 단위. 어느 기능이 하루 상한을 쓰는지 여기로 구분한다. */
    static final String FEATURE = "expo-search";

    /** 사용자는 KST 로 "19일" 이라고 말한다. 서버 시계가 어디에 있든 이 기준으로 읽어야 한다. */
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 너무 먼 미래를 물으면 회차 조회 범위가 과해진다. 종범님 by-date 와 맞춘 값이다. */
    private static final int MAX_RANGE_DAYS = 92;
    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_KEYWORD_LENGTH = 50;

    /**
     * 모든 박람회에 해당하는 말. keyword 에서 걷어낸다.
     *
     * <p>이게 남아 있으면 조건을 더 줄수록 결과가 이상해진다 - "유료 IT 박람회" 에서 '박람회' 가
     * 키워드로 남으면 제목에 그 단어가 없는 박람회가 통째로 빠진다. 프롬프트로도 막지만
     * <b>모델 출력은 사용자 입력과 같은 등급</b>이라 서버에서 한 번 더 거른다.
     */
    private static final Set<String> GENERIC_WORDS =
            Set.of("박람회", "전시회", "전시", "행사", "엑스포", "페어", "이벤트", "축제");

    private final GeminiClient gemini;
    private final ExpoQueryRepository expoQueryRepository;
    private final Clock clock;

    public SearchQueryParser(GeminiClient gemini, ExpoQueryRepository expoQueryRepository, Clock clock) {
        this.gemini = gemini;
        this.expoQueryRepository = expoQueryRepository;
        this.clock = clock;
    }

    /** LLM 응답을 받는 그릇. 파싱은 common-ai 가 한다. */
    public record Parsed(String region, String category, Boolean paid,
                         String dateFrom, String dateTo, String keyword) {
    }

    @Transactional(readOnly = true)
    public SearchFilter parse(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return SearchFilter.keywordOnly(null);
        }
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH);
        }
        if (!gemini.isAvailable()) {
            return SearchFilter.keywordOnly(trimmed);
        }

        List<String> regions = expoQueryRepository.findPublishedRegions();
        Parsed parsed = gemini.generateJson(FEATURE, prompt(trimmed, regions), Parsed.class);
        if (parsed == null) {
            log.info("search query not interpreted, falling back to keyword");
            return SearchFilter.keywordOnly(trimmed);
        }
        SearchFilter filter = validate(parsed, regions, trimmed);
        // parsed 는 모델이 준 그대로, filter 는 검증 뒤다. 둘을 같이 봐야 어디서 빠졌는지 알 수 있다 -
        // 모델이 응답 모양을 어기면 Jackson 이 모르는 필드를 버려 parsed 가 조용히 전부 null 이 된다.
        log.info("search interpreted: parsed={} filter={}", parsed, filter);
        return filter;
    }

    private String prompt(String query, List<String> regions) {
        return """
                검색 문장을 박람회 검색 필터로 바꿔 JSON 으로만 응답하세요 (설명 없이).

                오늘은 %s (한국 시간) 입니다. 상대 날짜는 이 날짜를 기준으로 절대 날짜로 바꾸세요.
                "이번 주"는 이번 주 월요일부터 일요일까지입니다. "주말"은 토요일과 일요일입니다.
                기간이면 dateFrom 과 dateTo 를 다르게, 하루면 둘을 같게 쓰세요.

                지역은 다음 목록에 있는 값만 그대로 쓰고, 없으면 null 로 두세요: %s
                분야는 다음 중 하나만 쓰고, 없으면 null 로 두세요: %s
                유료 여부는 "유료"·"돈 내는"이면 true, "무료"·"공짜"면 false, 언급이 없으면 null 입니다.
                keyword 에는 위 항목으로 옮기지 못한 말만 담고, 없으면 null 입니다.
                "박람회"·"전시회"·"행사"·"엑스포"·"페어"처럼 모든 박람회에 해당하는 말은 keyword 에 넣지 마세요.

                한 단어만 들어와도 조건이면 조건으로 옮기세요.

                아래는 오늘이 2026-01-05 월요일일 때의 예시입니다.
                입력: 무료
                출력: {"region":null,"category":null,"paid":false,"dateFrom":null,"dateTo":null,"keyword":null}
                입력: 이번 주 박람회
                출력: {"region":null,"category":null,"paid":null,"dateFrom":"2026-01-05","dateTo":"2026-01-11","keyword":null}
                입력: 벡스코에서 하는 IT 전시회
                출력: {"region":null,"category":"IT·전자","paid":null,"dateFrom":null,"dateTo":null,"keyword":"벡스코"}

                [문장]%s[/문장]

                위 문장에 대한 출력만, 아래 여섯 개 키를 가진 JSON 객체 하나로 응답하세요.
                다른 키로 감싸지 말고, 배열이나 설명을 덧붙이지 마세요.
                {"region":null,"category":null,"paid":null,"dateFrom":null,"dateTo":null,"keyword":null}
                """.formatted(today(), String.join(", ", regions),
                String.join(", ", ExpoCategories.ALLOWED), query);
    }

    /**
     * 모델이 준 값을 시스템이 아는 값으로만 좁힌다.
     *
     * <p>어긋난 값은 400 으로 거절하지 않고 <b>그 조건만 버린다</b> - 검색은 조건 하나가 틀려도
     * 나머지로 결과를 주는 게 맞다. 목록 조회의 카테고리 검증(400)과 의도적으로 다르다.
     */
    private SearchFilter validate(Parsed parsed, List<String> regions, String original) {
        String region = pick(parsed.region(), regions);
        String category = pick(parsed.category(), ExpoCategories.ALLOWED);

        LocalDate from = toDate(parsed.dateFrom());
        LocalDate to = toDate(parsed.dateTo());
        if (from == null || to == null || to.isBefore(from)) {
            from = null;
            to = null;
        } else if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            to = from.plusDays(MAX_RANGE_DAYS);
        }

        String keyword = cleanKeyword(parsed.keyword());
        SearchFilter filter = new SearchFilter(region, category, parsed.paid(), from, to, keyword);

        // 아무 조건도 못 뽑았으면 해석이 안 된 것과 같다. 원문을 키워드로 돌려준다.
        if (filter.isEmpty() && keyword == null) {
            return SearchFilter.keywordOnly(original);
        }
        return filter;
    }

    /**
     * 허용 목록에 있는 값만 남긴다.
     *
     * <p>null 을 먼저 거르는 이유는 {@code Set.of(...)}·{@code List.of(...)} 가
     * {@code contains(null)} 에 NullPointerException 을 던지기 때문이다. 언급되지 않은 조건은
     * 모델이 null 로 주는 것이 정상이므로, 이 방어가 없으면 평범한 검색이 500 으로 떨어진다.
     */
    private static String pick(String value, Collection<String> allowed) {
        return value != null && allowed.contains(value) ? value : null;
    }

    private LocalDate toDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            log.info("search query gave an unparsable date: {}", raw);
            return null;
        }
    }

    /** 일반 명사를 걷어낸 나머지만 남긴다. 다 걷히면 검색어가 없는 것과 같다. */
    private String cleanKeyword(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        String stripped = Arrays.stream(trimmed.split("\\s+"))
                .filter(word -> !GENERIC_WORDS.contains(word))
                .collect(Collectors.joining(" "))
                .trim();
        return stripped.isEmpty() ? null : stripped;
    }

    private String trimToNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > MAX_KEYWORD_LENGTH ? trimmed.substring(0, MAX_KEYWORD_LENGTH) : trimmed;
    }

    /** 요일까지 준다. 날짜만으로는 "이번 주" 의 시작·끝을 모델이 계산해야 해서 자주 틀린다. */
    private String today() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), KST);
        return today + " " + today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
    }
}
