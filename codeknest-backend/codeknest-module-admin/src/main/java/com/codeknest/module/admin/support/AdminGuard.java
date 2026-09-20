package com.codeknest.module.admin.support;

import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.common.security.SecurityContext;

/**
 * 管理员校验工具 — 服务层纵深防御（路径层规则之外的二次保障）
 */
public final class AdminGuard {

    private AdminGuard() {}

    /** 当前请求必须由 ROLE_ADMIN 发起，否则抛 40300 */
    public static void requireAdmin() {
        if (!"ROLE_ADMIN".equals(SecurityContext.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
