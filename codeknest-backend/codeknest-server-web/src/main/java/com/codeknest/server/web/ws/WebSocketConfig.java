package com.codeknest.server.web.ws;

import com.codeknest.module.account.message.push.NotificationPusher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置（仅 server-web）— 注册通知端点 /api/ws/notify 并暴露推送器实现
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotifyWebSocketHandler notifyHandler;
    private final WsAuthHandshakeInterceptor authInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notifyHandler, WsConstants.NOTIFY_ENDPOINT)
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns("*");
    }

    /** 将处理器适配为通知推送 SPI 实现（module-message 接口，admin 侧无此 Bean 自动 no-op） */
    @org.springframework.context.annotation.Bean
    public NotificationPusher notificationPusher(NotifyWebSocketHandler handler) {
        return handler::pushToUser;
    }
}
