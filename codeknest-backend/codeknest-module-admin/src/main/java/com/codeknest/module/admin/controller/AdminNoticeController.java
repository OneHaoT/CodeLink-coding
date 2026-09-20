package com.codeknest.module.admin.controller;

import com.codeknest.common.core.Result;
import com.codeknest.module.admin.service.AdminNoticeService;
import com.codeknest.module.content.post.entity.Notice;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 公告管理 /admin/notices
 */
@RestController
@RequestMapping("/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    @GetMapping
    public Result<List<Notice>> list() {
        return Result.ok(adminNoticeService.listAll());
    }

    @PostMapping
    public Result<Notice> create(@RequestBody Notice notice) {
        return Result.created(adminNoticeService.create(notice));
    }

    @PutMapping("/{id}")
    public Result<Notice> update(@PathVariable Long id, @RequestBody Notice notice) {
        return Result.ok(adminNoticeService.update(id, notice));
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminNoticeService.toggleStatus(id, status);
        return Result.ok("状态已更新", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminNoticeService.delete(id);
        return Result.ok("已删除", null);
    }
}
