package com.team1.expo.channel.service;

import com.team1.expo.channel.dto.ChannelResponse;
import com.team1.expo.channel.dto.CreateChannelRequest;
import com.team1.expo.common.exception.BusinessException;
import com.team1.expo.common.exception.ErrorCode;
import com.team1.expo.domain.channel.Channel;
import com.team1.expo.domain.channel.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;

    @Transactional
    public ChannelResponse create(Long ownerId, CreateChannelRequest request) {
        if (channelRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CHANNEL_NAME);
        }
        Channel channel = Channel.create(request.name(), ownerId, request.description());
        return ChannelResponse.from(channelRepository.save(channel));
    }

    @Transactional(readOnly = true)
    public Page<ChannelResponse> listMy(Long ownerId, Pageable pageable) {
        return channelRepository.findByOwnerId(ownerId, pageable)
                .map(ChannelResponse::from);
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
