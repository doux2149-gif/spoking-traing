package com.example.speech.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.speech.dto.LoginLogQuery;
import com.example.speech.entity.SysLoginLog;
import com.example.speech.entity.SysUser;
import com.example.speech.mapper.SysLoginLogMapper;
import com.example.speech.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录日志写入。异步落库, 不影响登录主流程。
 */
@Service
@RequiredArgsConstructor
public class LoginLogService {

    /** user_agent 列长度 */
    private static final int UA_MAX = 500;
    /** message 列长度 */
    private static final int MSG_MAX = 255;

    private final SysLoginLogMapper loginLogMapper;
    private final SysUserMapper userMapper;

    @Async
    public void recordSuccess(Long userId, String username, String ip, String userAgent) {
        save(userId, username, ip, userAgent, 1, "登录成功");
    }

    /** 登录失败: 用户存在则补全 userId, 用户不存在时 userId 为空 */
    @Async
    public void recordFailure(String username, String ip, String userAgent, String message) {
        Long userId = null;
        if (username != null && !username.isBlank()) {
            SysUser user = userMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username).last("LIMIT 1"));
            if (user != null) {
                userId = user.getId();
            }
        }
        save(userId, username, ip, userAgent, 0, message);
    }

    private void save(Long userId, String username, String ip, String userAgent, int status, String message) {
        try {
            SysLoginLog log = new SysLoginLog();
            log.setUserId(userId);
            log.setUsername(username);
            log.setIp(ip);
            log.setUserAgent(truncate(userAgent, UA_MAX));
            log.setStatus(status);
            log.setMessage(truncate(message, MSG_MAX));
            log.setLoginTime(LocalDateTime.now());
            loginLogMapper.insert(log);
        } catch (Exception e) {
            // 日志失败不能影响登录
            System.err.println("[LoginLogService] 写入登录日志失败: " + e.getMessage());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    /** 管理端分页查询, 返回 {total, rows} 对齐用户列表风格 */
    public Map<String, Object> pageLogs(LoginLogQuery query) {
        Page<SysLoginLog> page = loginLogMapper.selectPage(
                Page.of(query.getPageNum(), query.getPageSize()), buildWrapper(query));
        Map<String, Object> result = new HashMap<>();
        result.put("total", page.getTotal());
        result.put("rows", page.getRecords());
        return result;
    }

    /** 导出 CSV 用: 同样筛选条件但不分页 */
    public List<SysLoginLog> listForExport(LoginLogQuery query) {
        return loginLogMapper.selectList(buildWrapper(query));
    }

    private LambdaQueryWrapper<SysLoginLog> buildWrapper(LoginLogQuery query) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getUsername())) {
            wrapper.like(SysLoginLog::getUsername, query.getUsername());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysLoginLog::getStatus, query.getStatus());
        }
        if (query.getStartDate() != null) {
            wrapper.ge(SysLoginLog::getLoginTime, query.getStartDate().atStartOfDay());
        }
        if (query.getEndDate() != null) {
            wrapper.le(SysLoginLog::getLoginTime, query.getEndDate().atTime(LocalTime.MAX));
        }
        wrapper.orderByDesc(SysLoginLog::getLoginTime);
        return wrapper;
    }
}
