package com.example.speech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_checkin_stats")
public class UserCheckinStats {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("streak_count")
    private Integer streakCount;
    @TableField("max_streak")
    private Integer maxStreak;
    @TableField("total_days")
    private Integer totalDays;
    @TableField("total_seconds")
    private Integer totalSeconds;
    @TableField("total_rounds")
    private Integer totalRounds;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
