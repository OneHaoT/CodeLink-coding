package com.codeknest.module.account.auth.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 登录日志文档（MongoDB 集合 login_log）— 记录登录成功与失败
 */
@Data
@Document("login_log")
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
