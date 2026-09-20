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

    /**
     * 签名密钥（HS256 要求至少 256 位，即 32 个字符）。
     * <p>由环境变量 {@code JWT_SECRET} 提供；未配置时由
     * {@code JwtSecretBootstrapPostProcessor} 自动生成并落盘复用，故此处不设默认值。
     */
    private String secret;

    /** accessToken 有效期（秒），默认 2 小时 */
    private long accessTokenExpires = 7200;

    /** refreshToken 有效期（秒），默认 7 天 */
    private long refreshTokenExpires = 604800;
}
