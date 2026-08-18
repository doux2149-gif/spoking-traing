package com.example.speech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_record")
public class PracticeRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long sceneId;
    private String sceneName;
    private Integer rounds;
    private Integer duration;
    private Integer errorCount;
    private Integer score;
    private String summary;
    @TableField("create_time")
    private LocalDateTime createTime;
}
