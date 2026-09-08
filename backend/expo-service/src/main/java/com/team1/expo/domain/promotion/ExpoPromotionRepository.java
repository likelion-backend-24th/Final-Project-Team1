package com.team1.expo.domain.promotion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpoPromotionRepository extends JpaRepository<ExpoPromotion, Long> {

    List<ExpoPromotion> findByStatusOrderByPaidAtAsc(ExpoPromotionStatus status);

    Optional<ExpoPromotion> findByExpoIdAndStatus(Long expoId, ExpoPromotionStatus status);

    boolean existsByExpoIdAndStatus(Long expoId, ExpoPromotionStatus status);
}
