package com.example.speech.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;

public class SecurityUtils {

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        return null;
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (authority.getAuthority().equals("ROLE_ADMIN")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (authority.getAuthority().equals("ROLE_USER")) {
                return true;
            }
        }
        return false;
    }

    public static void assertIsAdmin() {
        if (!isAdmin()) {
            throw new SecurityException("权限不足，需要管理员权限");
        }
    }

    public static void assertDataOwnership(Long resourceUserId) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new SecurityException("未登录");
        }
        if (!isAdmin() && !currentUserId.equals(resourceUserId)) {
            throw new SecurityException("无权访问他人数据");
        }
    }
}
