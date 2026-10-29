package com.smartplanner.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CalendarSubscriptionRepository extends JpaRepository<CalendarSubscription, Long> {

    List<CalendarSubscription> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<CalendarSubscription> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndUrl(Long userId, String url);

    long countByUserId(Long userId);
}
