package com.codeknest.module.account.message.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知列表项
 */
@Data
public class NotificationVO {
    private Long id;
    private String type;
    private String title;
    private String content;
    private Long sourceUserId;
    private String sourceUsername;
    private String sourceUserAvatar;
    private Long sourcePostId;
    private Long sourceCommentId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
