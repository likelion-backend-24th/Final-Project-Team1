package com.team1.expo.internal;

import com.team1.expo.domain.channel.ChannelRepository;
import com.team1.expo.domain.expo.Expo;
import com.team1.expo.domain.expo.ExpoRepository;
import com.team1.expo.domain.expo.ExpoStatus;
import com.team1.expo.expo.dto.ExpoPublicationResponse;
import com.team1.expo.expo.service.ExpoPublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/expos")
@RequiredArgsConstructor
public class ExpoInternalController {

    /** 내 예약 한 화면이 넘길 수 있는 박람회 수를 훌쩍 넘는 값. 내부 호출부 실수를 막는 상한이다. */
    private static final int MAX_TITLE_IDS = 200;

    private final ExpoRepository expoRepository;
    private final ChannelRepository channelRepository;
    private final ExpoPublicationService expoPublicationService;

    // Envelope 없이 원본 반환 — reservation-service 파서가 이 형태로 읽음
    // title 은 현장 체크인 화면이 쓴다. 소유권 검증이 이미 이 API 를 부르므로 호출이 늘지 않는다.
    @GetMapping("/{expoId:\\d+}")
    public ResponseEntity<Map<String, Object>> getExpo(@PathVariable Long expoId) {
        return expoRepository.findById(expoId)
                .map(expo -> {
                    Long ownerId = channelRepository.findById(expo.getChannelId())
                            .map(ch -> ch.getOwnerId())
                            .orElseThrow();
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "expoId", expo.getId(),
                            "channelOwnerId", ownerId,
                            "status", expo.getStatus().name(),
                            "title", expo.getTitle() != null ? expo.getTitle() : ""
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 제목 일괄 조회. 내 예약 화면이 "무슨 박람회"인지 보여주는 데 쓴다.
     * 지난 예약의 박람회는 HIDDEN·CLOSED 일 수 있으므로 상태로 거르지 않는다.
     */
    @GetMapping("/titles")
    public List<Map<String, Object>> titles(@RequestParam List<Long> expoIds) {
        if (expoIds.isEmpty() || expoIds.size() > MAX_TITLE_IDS) {
            return List.of();
        }
        return expoRepository.findAllById(expoIds).stream()
                .map(e -> Map.<String, Object>of(
                        "expoId", e.getId(),
                        "title", e.getTitle() != null ? e.getTitle() : ""))
                .toList();
    }

    @GetMapping
    public List<Map<String, Object>> listPublished() {
        return expoRepository.findAll().stream()
                .filter(e -> ExpoStatus.PUBLISHED.equals(e.getStatus()))
                .map(e -> Map.<String, Object>of(
                        "expoId", e.getId(),
                        "title", e.getTitle() != null ? e.getTitle() : "",
                        "description", e.getDescription() != null ? e.getDescription() : ""))
                .toList();
    }

    /**
     * 자동 비공개(계약 2-3). 예약-Service 가 마지막 회차를 삭제하기 <b>직전</b>에 부른다.
     * 멱등이며, 이미 HIDDEN·CLOSED 면 상태를 바꾸지 않고 현재 값을 돌려준다.
     */
    @PatchMapping("/{expoId}/unpublish")
    public ExpoPublicationResponse unpublish(@PathVariable Long expoId) {
        return expoPublicationService.unpublish(expoId);
    }
}
