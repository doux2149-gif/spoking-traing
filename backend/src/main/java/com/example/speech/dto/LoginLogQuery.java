package com.example.speech.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 登录日志筛选参数: 用户名模糊、状态(1成功 0失败)、登录日期范围。
 */
@Data
public class LoginLogQuery {
    private String username;
    private Integer status;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
