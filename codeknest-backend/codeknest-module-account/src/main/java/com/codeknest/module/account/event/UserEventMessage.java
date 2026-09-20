package com.codeknest.module.account.event;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 统一用户事件消息 — 经 user.event.queue 投递，由 server-web 的 UserEventConsumer 消费。
 * <p>
 * 一个事件驱动两份 MongoDB 投影：始终写 user_action_log（操作日志），
 * 动作属于 feed 类时再写 user_activity（用户动态）。
 * {@code eventId} 由生产者生成并作为 Mongo 文档 _id，天然抵消 MQ 至少一次投递带来的重复。
 * <p>
 * 目标快照（文章标题/摘要/封面、评论节选、被关注者昵称）由生产者随消息带上 —— 生产侧本就已加载这些对象，
 * 请求路径不产生额外查询；发起人的昵称/头像由消费端补全（异步线程内查一次 MySQL）。
 */
@Data
public class UserEventMessage {

    /** 事件唯一标识（Mongo _id） */
    private String eventId;
    /** 见 UserActions */
    private String action;
    /** 动作发起人；登录失败（账号不存在）时为 null */
    private Long userId;
    /** 登录类事件才有值（登录失败时账号必填） */
    private String account;

    /** 目标类型：POST / COMMENT / USER */
    private String targetType;
    private Long targetId;

    // ---- 展示用快照 ----
    /** 评论类动作关联的文章（用于跳转） */
    private Long postId;
    private String postTitle;
    private String postSummary;
    private String postCoverImage;
    /** 评论正文节选 */
    private String commentExcerpt;
    /** 被关注者昵称（FOLLOW 用） */
    private String targetUsername;

    /** 是否成功（仅登录类区分成功/失败） */
    private Boolean success;
    private String failReason;
    private String ip;
    private String userAgent;
    private LocalDateTime occurredAt;

    public static UserEventMessage of(String action, Long userId, String account) {
        UserEventMessage m = new UserEventMessage();
        m.setEventId(UUID.randomUUID().toString());
        m.setAction(action);
        m.setUserId(userId);
        m.setAccount(account);
        m.setOccurredAt(LocalDateTime.now());
        return m;
    }

    public UserEventMessage target(String targetType, Long targetId) {
        this.targetType = targetType;
        this.targetId = targetId;
        return this;
    }

    /** 文章快照（发文/评论/点赞/收藏共用，生产侧对象已在手，无额外查询） */
    public UserEventMessage postSnapshot(Long postId, String title, String summary, String coverImage) {
        this.postId = postId;
        this.postTitle = title;
        this.postSummary = summary;
        this.postCoverImage = coverImage;
        return this;
    }

    /** 评论正文节选 */
    public UserEventMessage commentExcerpt(String excerpt) {
        this.commentExcerpt = excerpt;
        return this;
    }

    /** 被关注者昵称 */
    public UserEventMessage targetUsername(String username) {
        this.targetUsername = username;
        return this;
    }

    /** 登录类事件的成败标记 */
    public UserEventMessage result(boolean success, String failReason) {
        this.success = success;
        this.failReason = success ? null : failReason;
        return this;
    }
}