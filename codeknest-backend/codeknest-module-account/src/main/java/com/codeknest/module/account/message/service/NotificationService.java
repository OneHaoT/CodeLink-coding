package com.codeknest.module.account.message.service;

import com.codeknest.module.account.message.vo.NotificationVO;

import java.util.List;

/**
 * 通知服务 — 业务模块（点赞/评论/关注）通过本接口写入事件。
 * V1 同步落库；V2 改 RabbitMQ 异步时调用方无感知。
 */
public interface NotificationService {

    String LIKE_POST = "LIKE_POST";
    String LIKE_COMMENT = "LIKE_COMMENT";
    String COMMENT_POST = "COMMENT_POST";
    String REPLY_COMMENT = "REPLY_COMMENT";
    String NEW_FOLLOWER = "NEW_FOLLOWER";
    String SYSTEM = "SYSTEM";

    /**
     * 写入一条通知（targetUserId == sourceUserId 时自动忽略）
     */
    void notify(Long targetUserId, String type, Long sourceUserId,
                Long sourcePostId, Long sourceCommentId, String title, String content);

    List<NotificationVO> list(Long userId, String type);

    long unreadCount(Long userId);

    void markAllRead(Long userId);

    void markRead(Long userId, Long id);
}
