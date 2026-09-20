package com.codeknest.module.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志 t_audit_log
 * <p>该表无 updated_at / deleted，不继承 BaseEntity。
 */
@Data
@TableName("t_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String action;
    private String target;
    private String ip;
    private String detail;
    private LocalDateTime createdAt;
}
