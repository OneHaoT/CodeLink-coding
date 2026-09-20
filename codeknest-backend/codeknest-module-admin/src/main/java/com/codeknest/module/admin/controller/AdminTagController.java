package com.codeknest.module.admin.controller;

import com.codeknest.common.core.Result;
import com.codeknest.module.admin.dto.SaveTagDTO;
import com.codeknest.module.admin.service.AdminTagService;
import com.codeknest.module.content.post.entity.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签管理 /admin/tags
 */
@RestController
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {

    private final AdminTagService adminTagService;

    @GetMapping
    public Result<List<Tag>> list() {
        return Result.ok(adminTagService.listAll());
    }

    @PostMapping
    public Result<Tag> create(@Valid @RequestBody SaveTagDTO dto) {
        return Result.created(adminTagService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Tag> update(@PathVariable Long id, @Valid @RequestBody SaveTagDTO dto) {
        return Result.ok(adminTagService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminTagService.delete(id);
        return Result.ok("已删除", null);
    }
}
