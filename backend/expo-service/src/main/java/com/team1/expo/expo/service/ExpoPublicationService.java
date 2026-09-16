package com.team1.expo.expo.service;

import com.team1.expo.client.RoundClient;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.channel.Channel;
import com.team1.expo.domain.channel.ChannelRepository;
import com.team1.expo.domain.expo.Expo;
import com.team1.expo.domain.expo.ExpoRepository;
import com.team1.expo.expo.dto.ExpoPublicationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpoPublicationService {

    private final ExpoRepository expoRepository;
    private final ChannelRepository channelRepository;
    private final RoundClient roundClient;

    /**
     * 회차가 없어진 박람회의 자동 비공개(S9-3, 계약 2-3). 예약-Service 가 내부 호출로 트리거한다.
     *
     * <p>소유권을 확인하지 않는다 - 사람이 아니라 Service 가 부르는 경로이고,
     * {@code InternalTokenFilter} 가 이미 호출자를 가른다.
     *
     * <p>멱등이다. HIDDEN·CLOSED 는 그대로 두고 현재 상태를 돌려준다.
     */
    @Transactional
    public ExpoPublicationResponse unpublish(Long expoId) {
        Expo expo = expoRepository.findById(expoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        expo.unpublish();
        return ExpoPublicationResponse.from(expo);
    }

    /**
     * 박람회 공개 전환. 채널 소유자만 가능하며 상태별로 다음과 같이 처리한다.
     * - HIDDEN  : 회차가 하나라도 있으면 PUBLISHED로 전이, 없으면 400(상태는 HIDDEN 유지)
     * - PUBLISHED: 상태를 바꾸지 않고 멱등 200
     * - CLOSED  : 409 (역방향/재공개 불가)
     * 회차 확인이 실패(Timeout·5xx·연결 실패)하면 503으로 거절하고 상태를 변경하지 않는다.
     */
    @Transactional
    public ExpoPublicationResponse publish(Long expoId, Long requesterId) {
        Expo expo = expoRepository.findById(expoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        Channel channel = channelRepository.findById(expo.getChannelId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!channel.getOwnerId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        switch (expo.getStatus()) {
            case PUBLISHED -> {
                // 멱등: 이미 공개된 박람회는 상태를 바꾸지 않고 그대로 반환한다.
                return ExpoPublicationResponse.from(expo);
            }
            case CLOSED -> throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
            case HIDDEN -> {
                // 회차 확인이 실패하면 여기서 DEPENDENCY_UNAVAILABLE(503)이 던져지고 상태는 그대로 유지된다.
                if (!roundClient.existsByExpo(expoId)) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST);
                }
                expo.publish();
                return ExpoPublicationResponse.from(expo);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }
}
