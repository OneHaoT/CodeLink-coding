package com.codeknest.module.content.post.controller;

import com.codeknest.common.core.Result;
import com.codeknest.module.content.post.entity.Notice;
import com.codeknest.module.content.post.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 社区公告（前台公开只读）/notices
 */
@RestController
@RequestMapping("/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public Result<List<Notice>> list() {
        return Result.ok(noticeService.listPublished());
    }
}
