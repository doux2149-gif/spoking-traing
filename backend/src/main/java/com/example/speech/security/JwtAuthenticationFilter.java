package com.example.speech.security;

import com.example.speech.config.JwtProperties;
import com.example.speech.service.OnlineSessionService;
import com.example.speech.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final OnlineSessionService onlineSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            String tokenId = jwtUtil.getTokenIdFromToken(token);
            // 新 token 带 jti: 会话必须存在(未被强制下线/未退出); 旧 token 无 jti 则兼容放行
            if (tokenId != null && !onlineSessionService.sessionExists(tokenId)) {
                writeUnauthorized(response, "账号已在其他地方退出或被管理员强制下线，请重新登录");
                return;
            }
            Long userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);
            Long roleId = jwtUtil.getRoleIdFromToken(token);

            String roleKey = "USER";
            if (roleId != null && roleId == 1) {
                roleKey = "ADMIN";
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + roleKey))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 节流刷新最后活跃时间
            if (tokenId != null) {
                onlineSessionService.touch(tokenId);
            }
        }

        filterChain.doFilter(request, response);
    }

    /** 会话失效时直接返回 401 JSON */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(jwtProperties.getHeader());
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(jwtProperties.getPrefix())) {
            return bearerToken.substring(jwtProperties.getPrefix().length());
        }
        return null;
    }
}
