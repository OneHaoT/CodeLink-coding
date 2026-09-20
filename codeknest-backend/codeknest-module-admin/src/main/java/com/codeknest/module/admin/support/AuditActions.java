package com.codeknest.module.admin.support;

/**
 * 审计动作常量
 */
public final class AuditActions {

    private AuditActions() {}

    // 用户
    public static final String USER_ENABLE = "USER_ENABLE";
    public static final String USER_DISABLE = "USER_DISABLE";
    public static final String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    // 文章
    public static final String POST_AUDIT = "POST_AUDIT";
    public static final String POST_DELETE = "POST_DELETE";
    // 评论
    public static final String COMMENT_DELETE = "COMMENT_DELETE";
    // 标签 / 分类
    public static final String TAG_CREATE = "TAG_CREATE";
    public static final String TAG_UPDATE = "TAG_UPDATE";
    public static final String TAG_DELETE = "TAG_DELETE";
    public static final String CATEGORY_CREATE = "CATEGORY_CREATE";
    public static final String CATEGORY_UPDATE = "CATEGORY_UPDATE";
    public static final String CATEGORY_DELETE = "CATEGORY_DELETE";
    // 敏感词
    public static final String SENSITIVE_CREATE = "SENSITIVE_CREATE";
    public static final String SENSITIVE_UPDATE = "SENSITIVE_UPDATE";
    public static final String SENSITIVE_DELETE = "SENSITIVE_DELETE";
    // 公告
    public static final String NOTICE_CREATE = "NOTICE_CREATE";
    public static final String NOTICE_UPDATE = "NOTICE_UPDATE";
    public static final String NOTICE_STATUS = "NOTICE_STATUS";
    public static final String NOTICE_DELETE = "NOTICE_DELETE";
}
