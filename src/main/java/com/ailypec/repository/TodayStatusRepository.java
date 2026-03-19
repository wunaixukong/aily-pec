package com.ailypec.repository;

import com.ailypec.entity.TodayStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface TodayStatusRepository extends JpaRepository<TodayStatus, Long> {

    Optional<TodayStatus> findFirstByUserIdAndStatusDateOrderByCreateTimeDesc(Long userId, LocalDate statusDate);
}
