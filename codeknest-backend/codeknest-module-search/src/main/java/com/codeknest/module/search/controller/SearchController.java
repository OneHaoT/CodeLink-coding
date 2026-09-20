package com.codeknest.module.search.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.search.service.SearchService;
import com.codeknest.module.search.vo.SearchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 搜索控制器
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /** GET /api/search/posts — 全文检索（公开） */
    @GetMapping("/posts")
    public Result<PageVO<SearchVO>> search(@RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer size,
                                           @RequestParam(defaultValue = "relevance") String sort) {
        return Result.ok(searchService.search(q, page, size, sort));
    }

    /** GET /api/search/hotwords — 搜索热词 Top10（公开） */
    @GetMapping("/hotwords")
    public Result<List<String>> hotwords() {
        return Result.ok(searchService.hotwords(10));
    }

    /** POST /api/search/reindex — 全量重建索引（仅 ROLE_ADMIN） */
    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindex() {
        return Result.ok(Map.of("indexed", searchService.reindex()));
    }
}
