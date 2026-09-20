package com.codeknest.module.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志展示对象
 */
@Data
public class AuditLogVO {

    private Long id;
    private Long userId;
    private String operatorName;
    private String action;
    private String target;
    private String ip;
    private String detail;
    private LocalDateTime createdAt;
}
