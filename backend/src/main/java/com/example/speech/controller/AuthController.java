package com.example.speech.controller;

import com.example.speech.dto.LoginRequest;
import com.example.speech.dto.LoginResponse;
import com.example.speech.dto.RegisterRequest;
import com.example.speech.dto.Result;
import com.example.speech.entity.SysUser;
import com.example.speech.service.SysMenuService;
import com.example.speech.service.SysUserService;
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

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = userService.login(request);
            return Result.success(response);
        } catch (Exception e) {
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
}
