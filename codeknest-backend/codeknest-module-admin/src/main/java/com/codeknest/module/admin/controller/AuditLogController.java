package com.codeknest.module.admin.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.admin.dto.AuditLogQueryDTO;
import com.codeknest.module.admin.service.AuditLogService;
import com.codeknest.module.admin.vo.AuditLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志查询 /admin/audit-logs（只读）
 */
@RestController
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public Result<PageVO<AuditLogVO>> list(AuditLogQueryDTO query) {
        return Result.ok(auditLogService.page(query));
    }
}
