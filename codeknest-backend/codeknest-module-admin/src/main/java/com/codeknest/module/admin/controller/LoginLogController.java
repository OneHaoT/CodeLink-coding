package com.codeknest.module.admin.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.account.auth.service.LoginLogService;
import com.codeknest.module.account.auth.vo.LoginLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志查询 /admin/login-logs（只读，数据来自 MongoDB login_log 集合）
 */
@RestController
@RequestMapping("/admin/login-logs")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    /** GET /api/admin/login-logs — 分页查询（account 模糊 / success 精确） */
    @GetMapping
    public Result<PageVO<LoginLogVO>> list(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           @RequestParam(required = false) String account,
                                           @RequestParam(required = false) Boolean success) {
        return Result.ok(loginLogService.page(
                page == null ? 1 : page,
                size == null ? 10 : size,
                account,
                success));
    }
}
