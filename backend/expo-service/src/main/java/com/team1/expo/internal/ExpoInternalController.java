package com.team1.expo.internal;

import com.team1.expo.domain.channel.ChannelRepository;
import com.team1.expo.domain.expo.Expo;
import com.team1.expo.domain.expo.ExpoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/expos")
@RequiredArgsConstructor
public class ExpoInternalController {

    private final ExpoRepository expoRepository;
    private final ChannelRepository channelRepository;

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
}
