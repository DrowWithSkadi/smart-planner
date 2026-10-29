package com.smartplanner.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    List<CalendarEvent> findBySubscriptionId(Long subscriptionId);

    /** События пользователя, которые пересекаются с диапазоном [from, to). */
    @Query("""
            select e from CalendarEvent e join fetch e.subscription s
            where s.user.id = :userId and e.startAt < :to and e.endAt > :from
            order by e.startAt
            """)
    List<CalendarEvent> findForUser(@Param("userId") Long userId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    /** События, по которым пора отправить напоминание. */
    @Query("""
            select e from CalendarEvent e join fetch e.subscription s join fetch s.user
            where s.remindersEnabled = true and e.reminderSent = false
              and e.remindAt is not null and e.remindAt <= :now and e.startAt > :now
            """)
    List<CalendarEvent> findDueReminders(@Param("now") Instant now);

    @Modifying
    @Query("delete from CalendarEvent e where e.subscription.id = :subscriptionId")
    void deleteBySubscriptionId(@Param("subscriptionId") Long subscriptionId);
}
