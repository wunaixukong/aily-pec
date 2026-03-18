package com.ailypec.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 番茄钟配置属性
 * 用于管理默认配置和工作时间设置
 */
@Component
@ConfigurationProperties(prefix = "pomodoro")
public class PomodoroConfigProperties {

    /**
     * 默认配置
     */
    private DefaultConfig defaultConfig = new DefaultConfig();

    /**
     * 工作日配置
     */
    private WorkdayConfig workdayConfig = new WorkdayConfig();

    /**
     * 工作时间设置
     */
    private WorkTimeConfig workTime = new WorkTimeConfig();

    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    public void setDefaultConfig(DefaultConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    public WorkdayConfig getWorkdayConfig() {
        return workdayConfig;
    }

    public void setWorkdayConfig(WorkdayConfig workdayConfig) {
        this.workdayConfig = workdayConfig;
    }

    public WorkTimeConfig getWorkTime() {
        return workTime;
    }

    public void setWorkTime(WorkTimeConfig workTime) {
        this.workTime = workTime;
    }

    /**
     * 默认配置内部类
     */
    public static class DefaultConfig {
        private int workDuration = 25;
        private int breakDuration = 5;
        private boolean autoStart = false;

        // Getters and Setters
        public int getWorkDuration() { return workDuration; }
        public void setWorkDuration(int workDuration) { this.workDuration = workDuration; }

        public int getBreakDuration() { return breakDuration; }
        public void setBreakDuration(int breakDuration) { this.breakDuration = breakDuration; }

        public boolean isAutoStart() { return autoStart; }
        public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }
    }

    /**
     * 工作日配置内部类
     */
    public static class WorkdayConfig {
        private int workDuration = 50;
        private int breakDuration = 10;
        private boolean autoStart = true;

        // Getters and Setters
        public int getWorkDuration() { return workDuration; }
        public void setWorkDuration(int workDuration) { this.workDuration = workDuration; }

        public int getBreakDuration() { return breakDuration; }
        public void setBreakDuration(int breakDuration) { this.breakDuration = breakDuration; }

        public boolean isAutoStart() { return autoStart; }
        public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }
    }

    /**
     * 工作时间设置内部类
     */
    public static class WorkTimeConfig {
        private int startHour = 9;
        private int endHour = 18;
        private boolean enabled = true;

        // Getters and Setters
        public int getStartHour() { return startHour; }
        public void setStartHour(int startHour) { this.startHour = startHour; }

        public int getEndHour() { return endHour; }
        public void setEndHour(int endHour) { this.endHour = endHour; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
