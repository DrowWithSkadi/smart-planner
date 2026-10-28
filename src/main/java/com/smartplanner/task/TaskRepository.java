package com.smartplanner.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUserIdOrderByDueAtAsc(Long userId);

    List<Task> findByUserIdAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(
            Long userId, Instant from, Instant to);

    List<Task> findTop10ByUserIdAndDoneFalseAndDueAtGreaterThanEqualOrderByDueAtAsc(Long userId, Instant from);

    Optional<Task> findByIdAndUserId(Long id, Long userId);

    /** Задачи, по которым пора отправить напоминание. Пользователь подгружается сразу (join fetch). */
    @Query("""
            select t from Task t join fetch t.user
            where t.done = false and t.reminderSent = false
              and t.remindAt is not null and t.remindAt <= :now
            """)
    List<Task> findDueReminders(@Param("now") Instant now);
}
