package com.codeknest.common.core;

import lombok.Getter;

/**
 * 业务错误码
 */
@Getter
public enum ErrorCode {

    // 参数校验 40000-40099
    BAD_REQUEST(40000, "请求参数错误"),
    PARAM_VALID_FAIL(40001, "参数校验失败"),
    SENSITIVE_WORD_BLOCKED(40002, "内容包含敏感词"),

    // 认证 40100-40199
    UNAUTHORIZED(40100, "未登录或 Token 已过期"),
    TOKEN_INVALID(40101, "Token 无效"),
    TOKEN_EXPIRED(40102, "Token 已过期"),
    LOGIN_FAIL(40103, "账号或密码错误"),
    PASSWORD_ERROR(40104, "密码错误"),

    // 权限 40300-40399
    FORBIDDEN(40300, "权限不足"),
    NOT_OWNER(40301, "无权操作他人资源"),

    // 资源 40400-40499
    NOT_FOUND(40400, "资源不存在"),
    USER_NOT_FOUND(40401, "用户不存在"),
    POST_NOT_FOUND(40402, "文章不存在"),

    // 冲突 40900-40999
    CONFLICT(40900, "资源冲突"),
    EMAIL_EXISTS(40901, "邮箱已被注册"),
    USERNAME_EXISTS(40902, "昵称已被占用"),
    ALREADY_LIKED(40903, "已经点过赞了"),
    ALREADY_FOLLOWED(40904, "已经关注过了"),

    // 服务端 50000-50099
    INTERNAL_ERROR(50000, "系统内部错误，请稍后重试"),
    DB_ERROR(50001, "数据库操作失败"),
    REDIS_ERROR(50002, "Redis 操作失败"),
    ES_ERROR(50003, "Elasticsearch 操作失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
