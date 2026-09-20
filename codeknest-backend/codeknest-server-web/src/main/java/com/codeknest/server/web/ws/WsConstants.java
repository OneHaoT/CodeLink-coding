package com.codeknest.server.web.ws;

/** WebSocket 常量 */
public final class WsConstants {

    private WsConstants() {}

    /** 握手成功后放入 WebSocketSession attributes 的用户 ID key */
    public static final String ATTR_USER_ID = "wsUserId";
    public static final String ATTR_USERNAME = "wsUsername";

    /** 通知端点路径（相对 context-path /api） */
    public static final String NOTIFY_ENDPOINT = "/ws/notify";
}
