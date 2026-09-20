package com.codeknest.module.account.activity.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户动态展示对象（个人主页动态区 / 关注流）
 */
@Data
@Builder
public class UserActivityVO {

    private String id;
    private Long userId;
    private String actorUsername;
    private String actorAvatar;
    private String action;
    private String targetType;
    private Long targetId;

    private Long postId;
    private String postTitle;
    private String postSummary;
    private String postCoverImage;
    private String commentExcerpt;
    private String targetUsername;

    private LocalDateTime createdAt;
}