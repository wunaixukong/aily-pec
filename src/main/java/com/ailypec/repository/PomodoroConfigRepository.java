package com.ailypec.repository;

import com.ailypec.entity.PomodoroConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
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
     * 获取用户所有激活配置，按开始时间排序
     *
     * @param userId 用户ID
     * @param isActive 是否激活
     * @return 按 startTime 升序排列的配置列表
     */
    List<PomodoroConfig> findByUserIdAndIsActiveOrderByStartTimeAsc(Long userId, Boolean isActive);

    /**
     * 根据当前时间查找用户生效的配置
     * startTime <= currentTime < endTime
     *
     * @param userId 用户ID
     * @param currentTime 当前时间
     * @return 命中的配置（可选）
     */
    @Query("SELECT c FROM PomodoroConfig c WHERE c.userId = :userId AND c.isActive = true " +
           "AND c.startTime <= :currentTime AND c.endTime > :currentTime")
    Optional<PomodoroConfig> findActiveConfigByUserIdAndTime(
            @Param("userId") Long userId,
            @Param("currentTime") LocalTime currentTime);

    /**
     * 检测时间段重叠
     * 两段重叠条件：newStart < existingEnd AND newEnd > existingStart
     * 相邻不算重叠（如 9:00-12:00 和 12:00-18:00）
     *
     * @param userId 用户ID
     * @param startTime 新配置的开始时间
     * @param endTime 新配置的结束时间
     * @param excludeId 排除的配置ID（用于更新时排除自身）
     * @return 重叠的配置列表
     */
    @Query("SELECT c FROM PomodoroConfig c WHERE c.userId = :userId AND c.isActive = true " +
           "AND c.startTime < :endTime AND c.endTime > :startTime " +
           "AND (:excludeId IS NULL OR c.id <> :excludeId)")
    List<PomodoroConfig> findOverlappingConfigs(
            @Param("userId") Long userId,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludeId") Long excludeId);

}
