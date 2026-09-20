package com.codeknest.module.admin.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.admin.dto.AdminPostQueryDTO;
import com.codeknest.module.admin.dto.AuditPostDTO;
import com.codeknest.module.admin.service.AdminPostService;
import com.codeknest.module.admin.vo.AdminPostVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文章管理 /admin/posts
 */
@RestController
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final AdminPostService adminPostService;

    @GetMapping
    public Result<PageVO<AdminPostVO>> list(AdminPostQueryDTO query) {
        return Result.ok(adminPostService.page(query));
    }

    @PutMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditPostDTO dto) {
        adminPostService.audit(id, dto.getStatus(), dto.getReason());
        return Result.ok("审核完成", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminPostService.delete(id);
        return Result.ok("已删除", null);
    }
}
