package com.codeknest.module.admin.controller;

import com.codeknest.common.core.Result;
import com.codeknest.module.admin.dto.SaveCategoryDTO;
import com.codeknest.module.admin.service.AdminCategoryService;
import com.codeknest.module.content.post.entity.Category;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 分类管理 /admin/categories
 */
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    @GetMapping
    public Result<List<Category>> list() {
        return Result.ok(adminCategoryService.listAll());
    }

    @PostMapping
    public Result<Category> create(@Valid @RequestBody SaveCategoryDTO dto) {
        return Result.created(adminCategoryService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable Long id, @Valid @RequestBody SaveCategoryDTO dto) {
        return Result.ok(adminCategoryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminCategoryService.delete(id);
        return Result.ok("已删除", null);
    }
}
