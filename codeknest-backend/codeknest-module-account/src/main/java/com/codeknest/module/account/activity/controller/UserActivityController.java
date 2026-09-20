package com.codeknest.module.account.activity.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.account.activity.service.UserActivityService;
import com.codeknest.module.account.activity.vo.UserActivityVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户动态控制器（数据来自 MongoDB user_activity 读侧投影）
 */
@RestController
@RequiredArgsConstructor
public class UserActivityController {

    private final UserActivityService userActivityService;

    /** GET /api/users/{userId}/activities — 某用户的动态（游客可访问，个人主页动态区） */
    @GetMapping("/users/{userId}/activities")
    public Result<PageVO<UserActivityVO>> byUser(@PathVariable Long userId,
                                                 @RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(userActivityService.pageByUser(userId,
                page == null ? 1 : page, size == null ? 10 : size));
    }

    /** GET /api/activities/following — 关注流（我 + 我关注的人），需登录 */
    @GetMapping("/activities/following")
    public Result<PageVO<UserActivityVO>> following(@RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(userActivityService.pageFollowing(SecurityContext.requireUserId(),
                page == null ? 1 : page, size == null ? 10 : size));
    }
}