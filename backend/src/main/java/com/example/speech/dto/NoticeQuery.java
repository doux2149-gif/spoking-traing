package com.example.speech.dto;

import lombok.Data;

/**
 * 公告管理分页筛选: 标题模糊、类型(1通知 2维护)、状态(0草稿 1发布 2停用)。
 */
@Data
public class NoticeQuery {
    private String title;
    private Integer noticeType;
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
