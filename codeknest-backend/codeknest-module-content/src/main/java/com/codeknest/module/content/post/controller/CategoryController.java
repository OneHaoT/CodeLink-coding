package com.codeknest.module.content.post.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeknest.common.core.Result;
import com.codeknest.module.content.post.entity.Category;
import com.codeknest.module.content.post.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类控制器
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryMapper categoryMapper;

    /** GET /api/categories */
    @GetMapping
    public Result<List<Category>> list() {
        return Result.ok(categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder)));
    }
}
