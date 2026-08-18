package com.example.speech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("speaking_scene")
public class SpeakingScene {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String aiRole;
    private Integer difficulty;
    private String openingLine;
    private String systemPrompt;
    private String icon;
    private Integer sort;
    private Integer status;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
