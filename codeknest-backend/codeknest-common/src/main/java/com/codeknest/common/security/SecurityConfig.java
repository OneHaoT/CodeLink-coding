package com.codeknest.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 全局配置
 * — 无状态 JWT 认证 + 公开路由放行 + 跨域
 * — 提供基础 SecurityFilterChain，业务启动模块可按需覆盖
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;

    /** 公开路由（精确匹配的 POST 白名单） */
    public static final String[] PUBLIC_POST_PATHS = {
            "/auth/register",
            "/auth/login",
            "/auth/refresh",
            "/auth/password/reset"
    };

    /** 公开 GET 路由（只读资源匿名可访问） */
    public static final String[] PUBLIC_GET_PATHS = {
            "/posts",
            "/posts/*",
            "/posts/*/like/status",
            "/posts/*/favorite/status",
            "/notices",
            "/tags",
            "/tags/*",
            "/categories",
            "/categories/*",
            "/users/*",
            "/users/*/followers",
            "/users/*/following",
            "/users/*/activities",
            "/users/*/follow/status",
            "/comments",
            "/search/**",
            "/favicon.ico",
            "/error",
            "/files/**",
            "/image/**",
            // WebSocket 握手（鉴权由握手拦截器完成：校验 query 参数 access token）
            "/ws/**",
            "/actuator/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 跨域
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 关闭 CSRF（前后端分离）
                .csrf(AbstractHttpConfigurer::disable)
                // 无状态
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 公开路由放行
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()
                        // 个人资源必须登录（顺序优先于下面的公开 GET 白名单）
                        .requestMatchers("/users/me", "/users/me/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/posts/favorites", "/posts/drafts",
                                "/posts/drafts/**", "/notifications", "/notifications/**").authenticated()
                        // 管理后台接口：仅 ROLE_ADMIN（server-web 上无此路径，无影响）
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 搜索索引全量重建：仅 ROLE_ADMIN
                        .requestMatchers(HttpMethod.POST, "/search/reindex").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                // 添加 JWT 过滤器
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // 异常处理
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, resp, e) -> {
                            resp.setStatus(401);
                            resp.setContentType("application/json;charset=UTF-8");
                            resp.getWriter().write(
                                    objectMapper.writeValueAsString(
                                            com.codeknest.common.core.Result.fail(com.codeknest.common.core.ErrorCode.UNAUTHORIZED)
                                    )
                            );
                        })
                        .accessDeniedHandler((req, resp, e) -> {
                            resp.setStatus(403);
                            resp.setContentType("application/json;charset=UTF-8");
                            resp.getWriter().write(
                                    objectMapper.writeValueAsString(
                                            com.codeknest.common.core.Result.fail(com.codeknest.common.core.ErrorCode.FORBIDDEN)
                                    )
                            );
                        })
                );
        return http.build();
    }
}
