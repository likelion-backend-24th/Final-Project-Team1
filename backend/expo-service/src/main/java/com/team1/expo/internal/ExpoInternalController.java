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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/expos")
@RequiredArgsConstructor
public class ExpoInternalController {

    private final ExpoRepository expoRepository;
    private final ChannelRepository channelRepository;
    private final ExpoPublicationService expoPublicationService;

    // Envelope 없이 원본 반환 — reservation-service 파서가 이 형태로 읽음
    @GetMapping("/{expoId}")
    public ResponseEntity<Map<String, Object>> getExpo(@PathVariable Long expoId) {
        return expoRepository.findById(expoId)
                .map(expo -> {
                    Long ownerId = channelRepository.findById(expo.getChannelId())
                            .map(ch -> ch.getOwnerId())
                            .orElseThrow();
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "expoId", expo.getId(),
                            "channelOwnerId", ownerId,
                            "status", expo.getStatus().name()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
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
