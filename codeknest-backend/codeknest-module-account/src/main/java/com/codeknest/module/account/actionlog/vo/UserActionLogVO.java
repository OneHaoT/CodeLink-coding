package com.codeknest.module.account.actionlog.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志展示对象（后台 /admin/action-logs）
 */
@Data
@Builder
public class UserActionLogVO {

    private String id;
    private Long userId;
    private String account;
    private String action;
    private String targetType;
    private Long targetId;
    private String detail;
    private Boolean success;
    private String failReason;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}