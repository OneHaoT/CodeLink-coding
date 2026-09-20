package com.codeknest.module.account.actionlog.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 通用用户操作日志（MongoDB 集合 user_action_log）
 * <p>
 * 由「登录日志」泛化而来：action 字段区分登录成功/失败、登出、注册、发文、删文、评论、删评、关注、取关、点赞、收藏。
 * 事实源头一律在 MySQL 业务表，本集合只是挡在查询前的读侧投影（写入异步、失败不影响业务）。
 * <p>
 * 索引由 spring.data.mongodb.auto-index-creation 在启动时幂等创建，支撑后台按账号/用户/动作 + 时间倒序查询。
 */
@Data
@Document("user_action_log")
@CompoundIndexes({
        @CompoundIndex(name = "idx_account_created", def = "{'account': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_user_created", def = "{'userId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_action_created", def = "{'action': 1, 'createdAt': -1}")
})
public class UserActionLogDocument {

    /** 事件 id（生产者生成的 UUID） */
    @Id
    private String id;

    /** 动作发起人；登录失败（账号不存在）时为 null */
    private Long userId;
    /** 登录类事件的账号（昵称或邮箱） */
    private String account;
    /** 见 UserActions */
    private String action;
    private String targetType;
    private Long targetId;
    /** 可读描述，例：发表了文章《xxx》 / 评论了文章《xxx》 */
    private String detail;

    /** 仅登录类事件区分成败 */
    private Boolean success;
    private String failReason;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}