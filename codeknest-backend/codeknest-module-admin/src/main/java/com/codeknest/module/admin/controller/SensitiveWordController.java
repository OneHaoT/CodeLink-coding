package com.codeknest.module.admin.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.admin.service.SensitiveWordService;
import com.codeknest.module.content.post.entity.SensitiveWord;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 敏感词管理 /admin/sensitive-words
 */
@RestController
@RequestMapping("/admin/sensitive-words")
@RequiredArgsConstructor
public class SensitiveWordController {

    private final SensitiveWordService wordService;

    @GetMapping
    public Result<PageVO<SensitiveWord>> list(@RequestParam(required = false) String word,
                                              @RequestParam(required = false) String category,
                                              @RequestParam(defaultValue = "1") Long page,
                                              @RequestParam(defaultValue = "10") Long size) {
        return Result.ok(wordService.page(word, category, page, size));
    }

    @PostMapping
    public Result<SensitiveWord> create(@RequestBody SensitiveWord req) {
        return Result.created(wordService.create(req));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SensitiveWord req) {
        wordService.update(id, req);
        return Result.ok("更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wordService.delete(id);
        return Result.ok("已删除", null);
    }
}
