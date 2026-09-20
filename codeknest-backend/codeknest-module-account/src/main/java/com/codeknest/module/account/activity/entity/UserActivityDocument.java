package com.codeknest.module.account.activity.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 用户动态（MongoDB 集合 user_activity）— 用户动态流/关注流的读侧投影。
 * <p>
 * 只记录 feed 类动作（发文/评论/点赞/收藏/关注），由 user.event.queue 异步写入；
 * 事实源头在 MySQL（t_post / t_comment / t_post_like / t_post_favorite / t_user_follow），
 * 本集合不承载任何写路径，可随时从 MySQL 重放生成。
 * <p>
 * 索引由 spring.data.mongodb.auto-index-creation 在启动时幂等创建。
 */
@Data
@Document("user_activity")
@CompoundIndex(name = "idx_user_created", def = "{'userId': 1, 'createdAt': -1}")
public class UserActivityDocument {

    /** 事件 id（生产者生成的 UUID） */
    @Id
    private String id;

    /** 动作发起人（动态归属人） */
    private Long userId;
    /** 发起人昵称/头像快照（消费端补全） */
    private String actorUsername;
    private String actorAvatar;

    /** 见 UserActions 中的 feed 类动作 */
    private String action;
    private String targetType;
    private Long targetId;

    // ---- 展示快照：动态页无需回查文章/用户表 ----
    private Long postId;
    private String postTitle;
    private String postSummary;
    private String postCoverImage;
    private String commentExcerpt;
    private String targetUsername;

    private LocalDateTime createdAt;
}