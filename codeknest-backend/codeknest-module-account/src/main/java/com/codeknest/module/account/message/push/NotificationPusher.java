package com.codeknest.module.account.message.push;

/**
 * 通知实时推送 SPI — 由具备 WebSocket 能力的启动模块（server-web）提供实现；
 * 无实现时（如 server-admin）推送为 no-op，不影响通知落库
 */
public interface NotificationPusher {

    /**
     * 向指定在线用户推送通知消息
     *
     * @param userId 目标用户 ID（不在线时由实现方自行忽略）
     * @param json   消息 JSON 文本
     */
    void push(Long userId, String json);
}
