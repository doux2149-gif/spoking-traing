package com.example.speech.controller;

import com.example.speech.config.JwtProperties;
import com.example.speech.dto.Result;
import com.example.speech.entity.SysUserOnline;
import com.example.speech.service.OnlineSessionService;
import com.example.speech.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端在线用户: 在线会话列表 + 强制下线。
 */
@RestController
@RequestMapping("/api/system/online")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OnlineUserController {

    private final OnlineSessionService onlineSessionService;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;

    /** 在线会话列表, 可按用户名模糊搜, last_active_time DESC */
    @GetMapping
    public Result<List<SysUserOnline>> list(@RequestParam(required = false) String username) {
        return Result.success(onlineSessionService.listOnline(username));
    }

    /** 强制下线: 删除会话行, 对方下一请求即被拦截; 禁止踢自己 */
    @DeleteMapping("/{tokenId}")
    public Result<Void> forceOffline(@PathVariable String tokenId, HttpServletRequest request) {
        onlineSessionService.forceOffline(tokenId, currentTokenId(request));
        return Result.success();
    }

    /** 从当前请求 Authorization 头解析 jti, 用于禁止管理员踢自己 */
    private String currentTokenId(HttpServletRequest request) {
        String header = request.getHeader(jwtProperties.getHeader());
        if (StringUtils.hasText(header) && header.startsWith(jwtProperties.getPrefix())) {
            try {
                return jwtUtil.getTokenIdFromToken(header.substring(jwtProperties.getPrefix().length()));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}
