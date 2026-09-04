package com.team1.expo.channel.service;

import com.team1.expo.channel.dto.ChannelResponse;
import com.team1.expo.channel.dto.CreateChannelRequest;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.channel.Channel;
import com.team1.expo.domain.channel.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;

    @Transactional
    public ChannelResponse create(Long ownerId, CreateChannelRequest request) {
        if (channelRepository.existsByOwnerId(ownerId)) {
            throw new BusinessException(ErrorCode.CHANNEL_ALREADY_EXISTS);
        }
        if (channelRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CHANNEL_NAME);
        }
        Channel channel = Channel.create(request.name(), ownerId, request.description());
        return ChannelResponse.from(channelRepository.save(channel));
    }

    @Transactional(readOnly = true)
    public ChannelResponse getMyChannel(Long ownerId) {
        return channelRepository.findByOwnerId(ownerId)
                .map(ChannelResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ChannelResponse getOne(Long channelId, Long requesterId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!channel.getOwnerId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return ChannelResponse.from(channel);
    }
}
