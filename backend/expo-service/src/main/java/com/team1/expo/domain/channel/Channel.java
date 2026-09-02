package com.team1.expo.domain.channel;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.LocalDateTime;

@Entity
@Table(name = "channels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false)
    private Long ownerId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Channel create(String name, Long ownerId, String description) {
        Channel c = new Channel();
        c.name = name;
        c.ownerId = ownerId;
        c.description = description;
        c.createdAt = LocalDateTime.now(Clock.systemUTC());
        return c;
    }
}
