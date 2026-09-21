package com.team1.expo.expo.search;

import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.expo.Expo;
import com.team1.expo.expo.dto.ExpoSummaryResponse;
import com.team1.expo.expo.repository.ExpoQueryRepository;
import com.team1.expo.expo.service.ExpoQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 자연어 검색. 문장을 필터로 바꾼 뒤 <b>평범한 목록 조회</b>를 돌린다.
 *
 * <p>목록 API 에 얹지 않고 따로 둔 이유는 응답 모양이 다르기 때문이다 - 해석 결과를 함께 내려야
 * 화면이 그걸 보여줄 수 있다. 덕분에 {@code ExpoQueryController} 를 건드리지 않아, 같은 파일에서
 * 작업 중인 다른 사람과 충돌하지도 않는다.
 *
 * <p>조건을 DB 에서 전부 거르지 못해 <b>전체를 읽고 잘라내는 방식</b>을 쓴다. 유료·무료는 회차에서
 * 나오는 값이라 박람회 쿼리로는 걸러지지 않는다(날짜도 같은 이유로 #263 이 여기에 붙는다).
 * 박람회 수십 개 규모에서 허용하며, 수백 개가 되면 조건을 예약-Service 쪽으로 넘겨야 한다.
 */
@Service
@Transactional(readOnly = true)
public class ExpoSearchService {

    private static final int MAX_SIZE = 100;

    private final SearchQueryParser parser;
    private final ExpoQueryRepository expoQueryRepository;
    private final ExpoQueryService expoQueryService;

    public ExpoSearchService(SearchQueryParser parser,
                             ExpoQueryRepository expoQueryRepository,
                             ExpoQueryService expoQueryService) {
        this.parser = parser;
        this.expoQueryRepository = expoQueryRepository;
        this.expoQueryService = expoQueryService;
    }

    /** 해석 결과와 결과 페이지를 함께 돌려준다. 둘 다 있어야 화면이 조건을 보여줄 수 있다. */
    public record Result(SearchFilter filter, Page<ExpoSummaryResponse> page) {

        public boolean aiApplied() {
            return !filter.isEmpty();
        }
    }

    public Result search(String query, int page, int size) {
        if (query == null || query.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        int pageIndex = Math.max(page, 1) - 1;
        int pageSize = Math.min(Math.max(size, 1), MAX_SIZE);

        SearchFilter filter = parser.parse(query);
        List<Expo> matched = expoQueryRepository.findAllPublished(
                filter.region(), filter.category(), ExpoQueryService.normalizeKeyword(filter.keyword()));

        Map<Long, Boolean> paidByExpoId = expoQueryService.paidFlags(matched);
        List<Expo> filtered = matched.stream()
                .filter(expo -> matchesPaid(filter, paidByExpoId.get(expo.getId())))
                .sorted(Comparator.comparing(Expo::getCreatedAt).reversed())
                .toList();

        int from = pageIndex * pageSize;
        int to = Math.min(from + pageSize, filtered.size());
        List<ExpoSummaryResponse> slice = from >= filtered.size()
                ? List.of()
                : filtered.subList(from, to).stream()
                        .map(expo -> ExpoSummaryResponse.from(expo, paidByExpoId.get(expo.getId())))
                        .toList();

        return new Result(filter,
                new PageImpl<>(slice, PageRequest.of(pageIndex, pageSize), filtered.size()));
    }

    /**
     * 유료·무료를 모르는 박람회는 남긴다.
     *
     * <p>판정 실패(회차 조회 실패)와 "예약 가능한 회차가 없음" 이 둘 다 null 로 오는데, 어느
     * 쪽이든 <b>모른다는 이유로 결과에서 빼면 검색이 조용히 작아진다.</b> 배지가 안 보이는 것보다
     * 결과가 사라지는 쪽이 나쁘다.
     */
    private boolean matchesPaid(SearchFilter filter, Boolean paid) {
        return filter.paid() == null || paid == null || filter.paid().equals(paid);
    }
}
