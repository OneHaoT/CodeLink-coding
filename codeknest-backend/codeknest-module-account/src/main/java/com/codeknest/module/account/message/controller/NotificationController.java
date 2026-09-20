package com.codeknest.module.account.message.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.account.message.service.NotificationService;
import com.codeknest.module.account.message.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通知控制器
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** GET /api/notifications?type= */
    @GetMapping
    public Result<List<NotificationVO>> list(@RequestParam(required = false) String type) {
        return Result.ok(notificationService.list(SecurityContext.requireUserId(), type));
    }

    /** GET /api/notifications/unread/count */
    @GetMapping("/unread/count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(Map.of("count", notificationService.unreadCount(SecurityContext.requireUserId())));
    }

    /** PUT /api/notifications/read — 全部已读 */
    @PutMapping("/read")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(SecurityContext.requireUserId());
        return Result.ok();
    }

    /** PUT /api/notifications/{id}/read — 单条已读 */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(SecurityContext.requireUserId(), id);
        return Result.ok();
    }
}
