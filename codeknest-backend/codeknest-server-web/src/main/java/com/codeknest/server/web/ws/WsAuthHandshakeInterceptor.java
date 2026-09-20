package com.codeknest.server.web.ws;

import com.codeknest.common.security.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器 — 校验 query 参数中的 access token（浏览器 WebSocket 无法自定义请求头）。
 * 鉴权失败返回 false 拒绝握手（HTTP 401 由容器返回）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        String token = servletRequest.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
            log.warn("WebSocket 握手拒绝：缺少 token");
            return false;
        }
        Claims claims = jwtUtils.parseToken(token);
        if (claims == null || !"access".equals(jwtUtils.getType(claims))) {
            log.warn("WebSocket 握手拒绝：token 无效或类型不符");
            return false;
        }
        Long userId = jwtUtils.getUserId(claims);
        if (userId == null) {
            return false;
        }
        attributes.put(WsConstants.ATTR_USER_ID, userId);
        attributes.put(WsConstants.ATTR_USERNAME, jwtUtils.getUsername(claims));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
