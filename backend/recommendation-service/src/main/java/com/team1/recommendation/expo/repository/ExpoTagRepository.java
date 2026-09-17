package com.team1.recommendation.expo.repository;

import com.team1.recommendation.expo.entity.ExpoTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpoTagRepository extends JpaRepository<ExpoTag, Long> {

    List<ExpoTag> findByExpoId(Long expoId);

    void deleteByExpoId(Long expoId);

    @Query("""
            SELECT DISTINCT t.expoId FROM ExpoTag t
            WHERE t.tagValue IN :tags AND t.expoId <> :expoId
            ORDER BY t.expoId DESC
            LIMIT 10
            """)
    List<Long> findSimilarExpoIds(@Param("expoId") Long expoId, @Param("tags") List<String> tags);
}
