package com.example.speech.controller;

import com.example.speech.dto.LoginRequest;
import com.example.speech.dto.LoginResponse;
import com.example.speech.dto.RegisterRequest;
import com.example.speech.dto.Result;
import com.example.speech.entity.SysUser;
import com.example.speech.service.LoginLogService;
import com.example.speech.service.OnlineSessionService;
import com.example.speech.service.SysMenuService;
import com.example.speech.service.SysUserService;
import com.example.speech.util.IpUtils;
import com.example.speech.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService userService;
    private final SysMenuService menuService;
    private final JwtUtil jwtUtil;
    private final LoginLogService loginLogService;
    private final OnlineSessionService onlineSessionService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = IpUtils.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        try {
            LoginResponse response = userService.login(request);
            // 登录成功: 写登录日志 + 建立在线会话(jti)
            String tokenId = jwtUtil.getTokenIdFromToken(response.getToken());
            onlineSessionService.createSession(tokenId, response.getUserId(), response.getUsername(), ip, userAgent);
            loginLogService.recordSuccess(response.getUserId(), response.getUsername(), ip, userAgent);
            return Result.success(response);
        } catch (Exception e) {
            loginLogService.recordFailure(request.getUsername(), ip, userAgent, e.getMessage());
            return Result.error(401, e.getMessage());
        }
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        try {
            userService.register(request);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @GetMapping("/user-info")
    public Result<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            if (token == null || !jwtUtil.validateToken(token)) {
                return Result.error(401, "未登录或token已过期");
            }
            Long userId = jwtUtil.getUserIdFromToken(token);
            SysUser user = userService.getUserById(userId);

            Map<String, Object> data = new HashMap<>();
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("nickname", user.getNickname());
            data.put("avatar", user.getAvatar());
            data.put("email", user.getEmail());
            data.put("phone", user.getPhone());
            data.put("status", user.getStatus());
            data.put("roleId", user.getRoleId());

            return Result.success(data);
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }

    @GetMapping("/menus")
    public Result<?> getCurrentUserMenus(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            if (token == null || !jwtUtil.validateToken(token)) {
                return Result.error(401, "未登录或token已过期");
            }
            Long roleId = jwtUtil.getRoleIdFromToken(token);
            return Result.success(menuService.getMenusByRoleId(roleId));
        } catch (Exception e) {
            return Result.error(401, e.getMessage());
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    /** 退出登录: 注销在线会话(best-effort, 旧 token 无 jti 也返回成功) */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            if (token != null && jwtUtil.validateToken(token)) {
                String tokenId = jwtUtil.getTokenIdFromToken(token);
                onlineSessionService.removeSession(tokenId);
            }
        } catch (Exception ignored) {
            // 即使失败也让前端清除本地登录态
        }
        return Result.success();
    }
}
