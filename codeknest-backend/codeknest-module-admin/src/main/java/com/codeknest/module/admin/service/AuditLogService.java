package com.codeknest.module.admin.service;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.admin.dto.AuditLogQueryDTO;
import com.codeknest.module.admin.vo.AuditLogVO;

public interface AuditLogService {

    /**
     * 记录一条审计（独立事务，写入失败仅打日志，不影响业务）
     */
    void record(String action, String target, String detail);

    PageVO<AuditLogVO> page(AuditLogQueryDTO query);
}
