package com.ailypec.repository;

import com.ailypec.entity.ProgressPointer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProgressPointerRepository extends JpaRepository<ProgressPointer, Long> {

    Optional<ProgressPointer> findByUserId(Long userId);

}
