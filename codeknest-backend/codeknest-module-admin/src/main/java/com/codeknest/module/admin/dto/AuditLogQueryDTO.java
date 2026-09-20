package com.codeknest.module.admin.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 审计日志分页查询
 */
@Data
public class AuditLogQueryDTO {

    private Long page = 1L;
    private Long size = 10L;

    private String action;
    private Long userId;
    private LocalDate startDate;
    private LocalDate endDate;
}
