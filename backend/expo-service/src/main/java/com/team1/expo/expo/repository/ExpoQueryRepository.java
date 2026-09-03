package com.team1.expo.expo.repository;

import com.team1.expo.domain.expo.Expo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 공개 조회(#26) 전용 레포지토리. P2가 소유한 domain/expo/ExpoRepository를 건드리지 않기 위해
 * 별도 인터페이스로 분리한다(엔티티당 여러 Spring Data 레포지토리는 허용됨).
 */
public interface ExpoQueryRepository extends Repository<Expo, Long> {

    Optional<Expo> findById(Long id);

    /**
     * PUBLISHED 박람회만, 지역·카테고리는 값이 있을 때만 필터한다.
     */
    @Query("""
            select e from Expo e
            where e.status = com.team1.expo.domain.expo.ExpoStatus.PUBLISHED
              and (:region is null or e.region = :region)
              and (:category is null or e.category = :category)
            """)
    Page<Expo> findPublished(@Param("region") String region,
                             @Param("category") String category,
                             Pageable pageable);
}
