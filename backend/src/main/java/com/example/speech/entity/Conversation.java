package com.example.speech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("scene_id")
    private Long sceneId;
    private String title;
    @TableField("is_starred")
    private Integer isStarred;
    private Integer duration;
    @TableField("round_count")
    private Integer roundCount;
    @TableField("error_count")
    private Integer errorCount;
    @TableField("suggestion_count")
    private Integer suggestionCount;
    private Integer status;
    private String summary;
    @TableField("context_summary")
    private String contextSummary;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
