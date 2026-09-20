package com.codeknest.module.admin.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.admin.dto.AdminCommentQueryDTO;
import com.codeknest.module.admin.service.AdminCommentService;
import com.codeknest.module.admin.vo.AdminCommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评论管理 /admin/comments
 */
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
public class AdminCommentController {

    private final AdminCommentService adminCommentService;

    @GetMapping
    public Result<PageVO<AdminCommentVO>> list(AdminCommentQueryDTO query) {
        return Result.ok(adminCommentService.page(query));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminCommentService.delete(id);
        return Result.ok("已删除", null);
    }
}
