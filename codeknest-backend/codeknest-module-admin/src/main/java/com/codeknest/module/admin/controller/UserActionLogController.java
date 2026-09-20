package com.codeknest.module.admin.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.account.actionlog.service.UserActionLogService;
import com.codeknest.module.account.actionlog.vo.UserActionLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 用户操作日志查询 /admin/action-logs（只读，数据来自 MongoDB user_action_log 集合）
 * <p>
 * 由原「登录日志」泛化而来：action 覆盖登录/登出/注册/发文/评论/关注/点赞等。
 */
@RestController
@RequestMapping("/admin/action-logs")
@RequiredArgsConstructor
public class UserActionLogController {

    private final UserActionLogService userActionLogService;

    /** GET /api/admin/action-logs — 分页查询（action 精确 / account 模糊 / userId 精确 / 时间区间） */
    @GetMapping
    public Result<PageVO<UserActionLogVO>> list(@RequestParam(defaultValue = "1") Integer page,
                                                @RequestParam(defaultValue = "10") Integer size,
                                                @RequestParam(required = false) String action,
                                                @RequestParam(required = false) String account,
                                                @RequestParam(required = false) Long userId,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return Result.ok(userActionLogService.page(
                page == null ? 1 : page,
                size == null ? 10 : size,
                action,
                account,
                userId,
                start,
                end));
    }
}