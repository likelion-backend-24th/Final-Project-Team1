package com.team1.expo.channel.dto;

import com.team1.expo.domain.channel.Channel;

import java.time.LocalDateTime;

public record ChannelResponse(
        Long id,
        String name,
        String description,
        Long ownerId,
        LocalDateTime createdAt
) {
    public static ChannelResponse from(Channel channel) {
        return new ChannelResponse(
                channel.getId(),
                channel.getName(),
                channel.getDescription(),
                channel.getOwnerId(),
                channel.getCreatedAt()
        );
    }
}
