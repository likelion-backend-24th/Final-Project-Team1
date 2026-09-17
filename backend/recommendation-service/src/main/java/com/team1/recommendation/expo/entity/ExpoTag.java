package com.team1.recommendation.expo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "expo_tags")
public class ExpoTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expo_id", nullable = false)
    private Long expoId;

    @Column(name = "tag_value", nullable = false, length = 100)
    private String tagValue;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ExpoTag() {}

    public static ExpoTag of(Long expoId, String tagValue, String summary, LocalDateTime now) {
        ExpoTag tag = new ExpoTag();
        tag.expoId = expoId;
        tag.tagValue = tagValue;
        tag.summary = summary;
        tag.createdAt = now;
        return tag;
    }
}
