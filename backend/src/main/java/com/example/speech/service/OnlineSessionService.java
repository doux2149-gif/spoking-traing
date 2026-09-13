package com.example.speech.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.speech.entity.SysUserOnline;
import com.example.speech.mapper.SysUserOnlineMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线会话管理: 登录建会话、按 jti 校验存在、节流刷新活跃时间、退出/踢人删会话。
 * 无 Redis, 用 MySQL 表 sys_user_online 承载。
 */
@Service
@RequiredArgsConstructor
public class OnlineSessionService {

    /** 活跃时间写库节流间隔(秒) */
    private static final long TOUCH_INTERVAL_SECONDS = 60;

    private final SysUserOnlineMapper onlineMapper;

    /** jti -> 上次刷新活跃时间的秒数, 避免每个请求都写库 */
    private final ConcurrentHashMap<String, Long> lastTouchEpochSeconds = new ConcurrentHashMap<>();

    public void createSession(String tokenId, Long userId, String username, String ip, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        SysUserOnline session = new SysUserOnline();
        session.setTokenId(tokenId);
        session.setUserId(userId);
        session.setUsername(username);
        session.setLoginIp(ip);
        session.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent);
        session.setLoginTime(now);
        session.setLastActiveTime(now);
        // 同一 jti 不会重复, 防极端重放
        onlineMapper.deleteById(tokenId);
        onlineMapper.insert(session);
        lastTouchEpochSeconds.put(tokenId, now.toEpochSecond(java.time.ZoneOffset.UTC));
    }

    public boolean sessionExists(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }
        return onlineMapper.selectById(tokenId) != null;
    }

    /** 节流刷新最后活跃时间 */
    public void touch(String tokenId) {
        if (tokenId == null) {
            return;
        }
        long nowSec = System.currentTimeMillis() / 1000L;
        Long last = lastTouchEpochSeconds.get(tokenId);
        if (last != null && nowSec - last < TOUCH_INTERVAL_SECONDS) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(TOUCH_INTERVAL_SECONDS);
        int updated = onlineMapper.touchActiveTime(tokenId, now, threshold);
        if (updated > 0) {
            lastTouchEpochSeconds.put(tokenId, nowSec);
        }
    }

    public void removeSession(String tokenId) {
        if (tokenId != null && !tokenId.isBlank()) {
            onlineMapper.deleteById(tokenId);
            lastTouchEpochSeconds.remove(tokenId);
        }
    }

    /** 在线会话列表, 可按用户名模糊搜索, 按最后活跃时间倒序 */
    public List<SysUserOnline> listOnline(String username) {
        LambdaQueryWrapper<SysUserOnline> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(SysUserOnline::getUsername, username);
        }
        wrapper.orderByDesc(SysUserOnline::getLastActiveTime);
        return onlineMapper.selectList(wrapper);
    }

    /**
     * 强制下线: 删除会话行即失效, 对方下一请求被过滤器拦截。
     * 禁止踢当前请求自身的会话。
     */
    public void forceOffline(String tokenId, String currentTokenId) {
        if (tokenId != null && tokenId.equals(currentTokenId)) {
            throw new IllegalArgumentException("不能强制下线自己");
        }
        onlineMapper.deleteById(tokenId);
        lastTouchEpochSeconds.remove(tokenId);
    }
}
