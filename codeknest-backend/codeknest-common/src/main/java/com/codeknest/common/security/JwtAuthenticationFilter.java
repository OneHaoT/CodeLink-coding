package com.codeknest.common.security;

import com.codeknest.common.core.ErrorCode;
import com.codeknest.common.core.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器 — 从 Authorization 头解析 Token，写入 SecurityContext
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;

    /** 被禁用账号的 access token 黑名单 key（与 AdminUserServiceImpl 写入侧一致） */
    private static final String ACCESS_TOKEN_BLACKLIST_KEY = "blacklist:access_token:%d";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null) {
                Claims claims = jwtUtils.parseToken(token);
                if (claims != null
                        && (claims.getExpiration() == null || claims.getExpiration().after(new java.util.Date()))) {
                    String type = jwtUtils.getType(claims);
                    // 只接受 accessToken 进入业务接口
                    if ("access".equals(type)) {
                        Long userId = jwtUtils.getUserId(claims);
                        // 禁用账号黑名单校验：命中则视为 token 失效，立即下线
                        if (userId != null && isAccessBlacklisted(userId)) {
                            writeUnauthorized(response);
                            return;
                        }
                        String username = jwtUtils.getUsername(claims);
                        String role = jwtUtils.getRole(claims);
                        SecurityContext.set(new SecurityContext.CurrentUser(userId, username, role));
                        // 同步到 Spring Security 上下文，授权规则才能生效
                        List<SimpleGrantedAuthority> authorities = role == null ? List.of()
                                : List.of(new SimpleGrantedAuthority(role));
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userId, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } else {
                    // Token 不合法或已过期，直接拒绝
                    writeUnauthorized(response);
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    /**
     * 判断用户是否在 access token 黑名单中（账号被禁用）。
     * Redis 不可用时降级放行，避免全局不可用（与项目缓存降级策略一致）。
     */
    private boolean isAccessBlacklisted(Long userId) {
        try {
            Boolean hit = redis.hasKey(String.format(ACCESS_TOKEN_BLACKLIST_KEY, userId));
            return Boolean.TRUE.equals(hit);
        } catch (Exception ex) {
            log.warn("Access token blacklist check failed for user {}: {}", userId, ex.getMessage());
            return false;
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(Result.fail(ErrorCode.TOKEN_INVALID))
        );
    }
}
