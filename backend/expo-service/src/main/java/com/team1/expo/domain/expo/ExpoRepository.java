package com.team1.expo.domain.expo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ExpoRepository extends JpaRepository<Expo, Long> {

    @Modifying
    @Query("UPDATE Expo e SET e.status = :to, e.closedAt = :closedAt, e.updatedAt = :closedAt " +
           "WHERE e.id IN :ids AND e.status = :from")
    int closeByIds(@Param("ids") List<Long> ids,
                   @Param("from") ExpoStatus from,
                   @Param("to") ExpoStatus to,
                   @Param("closedAt") LocalDateTime closedAt);
}
