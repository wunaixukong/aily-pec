package com.ailypec.repository;

import com.ailypec.entity.PomodoroConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 番茄钟配置数据访问层
 */
@Repository
public interface PomodoroConfigRepository extends JpaRepository<PomodoroConfig, Long> {

    /**
     * 根据用户ID查询配置
     *
     * @param userId 用户ID
     * @return 配置列表
     */
    List<PomodoroConfig> findByUserId(Long userId);

    /**
     * 根据用户ID查询激活的配置
     *
     * @param userId 用户ID
     * @param isActive 是否激活
     * @return 配置列表
     */
    List<PomodoroConfig> findByUserIdAndIsActive(Long userId, Boolean isActive);

    /**
     * 根据用户ID和激活状态查询单个配置
     *
     * @param userId 用户ID
     * @param isActive 是否激活
     * @return 配置（可选）
     */
    Optional<PomodoroConfig> findFirstByUserIdAndIsActive(Long userId, Boolean isActive);

}