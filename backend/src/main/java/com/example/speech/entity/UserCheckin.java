package com.example.speech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_checkin")
public class UserCheckin {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("checkin_date")
    private LocalDate checkinDate;
    @TableField("checkin_type")
    private Integer checkinType;
    @TableField("total_seconds")
    private Integer totalSeconds;
    @TableField("total_rounds")
    private Integer totalRounds;
    @TableField("total_errors")
    private Integer totalErrors;
    @TableField("total_suggestions")
    private Integer totalSuggestions;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
