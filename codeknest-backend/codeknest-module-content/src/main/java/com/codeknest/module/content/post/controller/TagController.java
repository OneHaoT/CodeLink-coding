package com.codeknest.module.content.post.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeknest.common.core.Result;
import com.codeknest.module.content.post.entity.Tag;
import com.codeknest.module.content.post.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签控制器
 */
@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagMapper tagMapper;

    /** GET /api/tags — 全部标签（按文章数倒序） */
    @GetMapping
    public Result<List<Tag>> list() {
        return Result.ok(tagMapper.selectList(
                new LambdaQueryWrapper<Tag>().orderByDesc(Tag::getPostCount).orderByAsc(Tag::getId)));
    }
}
