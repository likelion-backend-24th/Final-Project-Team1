package com.team1.expo.expo.repository;

import com.team1.expo.domain.expo.Expo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 공개 조회(#26) 전용 레포지토리. P2가 소유한 domain/expo/ExpoRepository를 건드리지 않기 위해
 * 별도 인터페이스로 분리한다(엔티티당 여러 Spring Data 레포지토리는 허용됨).
 */
public interface ExpoQueryRepository extends Repository<Expo, Long> {

    Optional<Expo> findById(Long id);

    /**
     * PUBLISHED 박람회만, 지역·카테고리·키워드는 값이 있을 때만 필터한다.
     * 키워드는 제목·소개문에 포함되는지로 판단한다.
     */
    @Query("""
            select e from Expo e
            where e.status = com.team1.expo.domain.expo.ExpoStatus.PUBLISHED
              and (:region is null or e.region = :region)
              and (:category is null or e.category = :category)
              and (:keyword is null or lower(e.title) like lower(concat('%', :keyword, '%'))
                                     or lower(e.description) like lower(concat('%', :keyword, '%')))
            """)
    Page<Expo> findPublished(@Param("region") String region,
                             @Param("category") String category,
                             @Param("keyword") String keyword,
                             Pageable pageable);

    @Query("""
            select e from Expo e
            where e.status = com.team1.expo.domain.expo.ExpoStatus.PUBLISHED
              and (:region is null or e.region = :region)
              and (:category is null or e.category = :category)
              and (:keyword is null or lower(e.title) like lower(concat('%', :keyword, '%'))
                                     or lower(e.description) like lower(concat('%', :keyword, '%')))
            """)
    List<Expo> findAllPublished(@Param("region") String region,
                                @Param("category") String category,
                                @Param("keyword") String keyword);
}
