package com.codeknest.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "codeknest.jwt")
public class JwtProperties {

    /** 签名密钥（至少 256 bits 对应 HS256） */
    private String secret = "change-me-in-prod-at-least-256-bits-long-key-here-12345";

    /** accessToken 有效期（秒），默认 2 小时 */
    private long accessTokenExpires = 7200;

    /** refreshToken 有效期（秒），默认 7 天 */
    private long refreshTokenExpires = 604800;
}
