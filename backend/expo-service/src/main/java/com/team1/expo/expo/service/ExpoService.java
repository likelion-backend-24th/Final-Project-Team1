package com.team1.expo.expo.service;

import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.channel.ChannelRepository;
import com.team1.expo.domain.expo.Expo;
import com.team1.expo.domain.expo.ExpoRepository;
import com.team1.expo.expo.dto.CreateExpoRequest;
import com.team1.expo.expo.dto.ExpoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpoService {

    private final ExpoRepository expoRepository;
    private final ChannelRepository channelRepository;

    @Transactional
    public ExpoResponse create(Long requesterId, CreateExpoRequest request) {
        channelRepository.findById(request.channelId())
                .filter(c -> c.getOwnerId().equals(requesterId))
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        Expo expo = Expo.create(
                request.channelId(),
                request.title(),
                request.description(),
                request.venue(),
                request.region(),
                request.category(),
                request.thumbnailUrl()
        );
        return ExpoResponse.from(expoRepository.save(expo));
    }
}
