package com.codeknest.module.account.auth.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 登录日志文档（MongoDB 集合 login_log）— 记录登录成功与失败
 * <p>
 * 索引由 spring.data.mongodb.auto-index-creation 在启动时幂等创建，
 * 支撑后台按账号 + 时间倒序的登录日志查询。
 */
@Data
@Document("login_log")
@CompoundIndex(name = "idx_account_created", def = "{'account': 1, 'createdAt': -1}")
public class LoginLogDocument {

    @Id
    private String id;

    /** 成功时有值；账号不存在时为 null */
    private Long userId;
    /** 尝试登录的账号（昵称或邮箱） */
    private String account;
    private Boolean success;
    /** 失败原因（成功时为 null） */
    private String failReason;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}
