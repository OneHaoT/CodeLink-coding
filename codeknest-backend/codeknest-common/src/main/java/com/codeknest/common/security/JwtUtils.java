package com.codeknest.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类 — 签发、解析、验证
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 签发 accessToken
     */
    public String generateAccessToken(Long userId, String username, String role) {
        return buildToken(userId, username, role, "access", jwtProperties.getAccessTokenExpires());
    }

    /**
     * 签发 refreshToken
     */
    public String generateRefreshToken(Long userId, String username) {
        return buildToken(userId, username, null, "refresh", jwtProperties.getRefreshTokenExpires());
    }

    private String buildToken(Long userId, String username, String role, String type, long ttlSeconds) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("type", type);
        if (role != null) claims.put("role", role);

        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlSeconds * 1000L))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token，返回 Claims（过期/无效返回 null）
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            log.debug("JWT expired: {}", ex.getMessage());
            return ex.getClaims(); // 过期也把 claims 返回，让上层做区分
        } catch (JwtException ex) {
            log.warn("JWT parse failed: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 验证 Token 是否有效（签名正确且未过期）
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            if (claims == null) return false;
            if (claims.getExpiration() != null && claims.getExpiration().before(new Date())) {
                return false;
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 从 Claims 提取 userId
     */
    public Long getUserId(Claims claims) {
        Object v = claims.get("userId");
        return v != null ? Long.valueOf(v.toString()) : null;
    }

    public String getUsername(Claims claims) {
        Object v = claims.get("username");
        return v != null ? v.toString() : null;
    }

    public String getRole(Claims claims) {
        Object v = claims.get("role");
        return v != null ? v.toString() : null;
    }

    public String getType(Claims claims) {
        Object v = claims.get("type");
        return v != null ? v.toString() : null;
    }
}
