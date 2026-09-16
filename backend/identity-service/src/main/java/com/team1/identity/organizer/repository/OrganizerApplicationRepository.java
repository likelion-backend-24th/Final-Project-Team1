package com.team1.identity.organizer.repository;

import com.team1.identity.organizer.entity.OrganizerApplication;
import com.team1.identity.organizer.entity.OrganizerApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizerApplicationRepository extends JpaRepository<OrganizerApplication, Long> {

    Optional<OrganizerApplication> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndStatus(Long userId, OrganizerApplicationStatus status);

    List<OrganizerApplication> findByStatusOrderByCreatedAtAsc(OrganizerApplicationStatus status);
}
