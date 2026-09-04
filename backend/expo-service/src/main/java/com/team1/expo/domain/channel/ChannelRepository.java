package com.team1.expo.domain.channel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Page<Channel> findByOwnerId(Long ownerId, Pageable pageable);
    boolean existsByName(String name);
    boolean existsByOwnerId(Long ownerId);
}
