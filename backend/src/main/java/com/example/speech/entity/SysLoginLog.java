package com.example.speech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志。status: 1=成功 0=失败。
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    @TableField("user_id")
    private Long userId;
    private String ip;
    @TableField("user_agent")
    private String userAgent;
    private Integer status;
    private String message;
    @TableField("login_time")
    private LocalDateTime loginTime;
}
