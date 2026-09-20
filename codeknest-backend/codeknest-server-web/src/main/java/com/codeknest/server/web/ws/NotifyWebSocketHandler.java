package com.codeknest.server.web.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知 WebSocket 处理器 — 本地 session 管理（单实例部署；多实例需引入 Redis 广播，暂不做）。
 * 同一用户多标签页登录：一个 userId 对应多个 session 全部推送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyWebSocketHandler extends TextWebSocketHandler {

    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userIdOf(session);
        if (userId == null) {
            closeQuietly(session);
            return;
        }
        sessionsByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("WebSocket 已连接：userId={}, sessionId={}, 在线连接数={}", userId, session.getId(), sessionsByUser.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId == null) return;
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByUser.remove(userId);
            }
        }
        log.debug("WebSocket 已断开：userId={}, status={}", userId, status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端心跳/杂散消息：忽略，不回复
    }

    /** 向指定用户的所有在线会话推送 JSON 消息（不在线静默忽略） */
    public void pushToUser(Long userId, String json) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) return;
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) continue;
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                log.warn("WebSocket 推送失败：userId={}, sessionId={}", userId, session.getId(), e);
            }
        }
    }

    /** 当前在线用户数（监控用） */
    public int onlineUserCount() {
        return sessionsByUser.size();
    }

    private Long userIdOf(WebSocketSession session) {
        Object userId = session.getAttributes().get(WsConstants.ATTR_USER_ID);
        return userId instanceof Long l ? l : null;
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignored) {
        }
    }
}
