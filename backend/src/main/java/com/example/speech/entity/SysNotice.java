package com.example.speech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统公告。
 * noticeType: 1=通知 2=维护; displayType: 1=横幅 2=弹窗; status: 0=草稿 1=已发布 2=已停用。
 */
@Data
@TableName("sys_notice")
public class SysNotice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    @TableField("notice_type")
    private Integer noticeType;
    @TableField("display_type")
    private Integer displayType;
    private Integer status;
    @TableField("publish_start")
    private LocalDateTime publishStart;
    @TableField("publish_end")
    private LocalDateTime publishEnd;
    @TableField("create_by")
    private Long createBy;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
