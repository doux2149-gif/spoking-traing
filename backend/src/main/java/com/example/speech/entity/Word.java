package com.example.speech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("word")
public class Word {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String english;
    private String chinese;
    private String phonetic;
    @TableField("part_of_speech")
    private String partOfSpeech;
    private String category;
    private Integer difficulty;
    @TableField("example_sentence")
    private String exampleSentence;
    @TableField("example_translation")
    private String exampleTranslation;
    private String source;    // UPLOAD / GENERATED
    @TableField("is_published")
    private Integer isPublished;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
