package com.example.speech.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线用户会话, 主键为 JWT 的 jti; 记录被删除即表示会话失效(强制下线/退出登录)。
 */
@Data
@TableName("sys_user_online")
public class SysUserOnline {
    @TableId("token_id")
    private String tokenId;
    @TableField("user_id")
    private Long userId;
    private String username;
    @TableField("login_ip")
    private String loginIp;
    @TableField("user_agent")
    private String userAgent;
    @TableField("login_time")
    private LocalDateTime loginTime;
    @TableField("last_active_time")
    private LocalDateTime lastActiveTime;
}
