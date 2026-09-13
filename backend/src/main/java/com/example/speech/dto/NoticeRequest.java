package com.example.speech.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告新增/编辑请求。时间可空(空=长期有效), 前端传 yyyy-MM-dd HH:mm:ss。
 */
@Data
public class NoticeRequest {
    @NotBlank(message = "公告标题不能为空")
    private String title;
    @NotBlank(message = "公告内容不能为空")
    private String content;
    @NotNull(message = "公告类型不能为空")
    private Integer noticeType;
    @NotNull(message = "展示方式不能为空")
    private Integer displayType;
    /** 0草稿 1发布 2停用, 缺省按草稿处理 */
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishStart;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishEnd;
}
