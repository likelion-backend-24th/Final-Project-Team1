package com.team1.expo.domain.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findByOwnerId(Long ownerId);
    boolean existsByName(String name);
    boolean existsByOwnerId(Long ownerId);
}
