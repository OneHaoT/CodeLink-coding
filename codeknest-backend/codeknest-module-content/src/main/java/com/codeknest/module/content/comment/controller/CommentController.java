package com.codeknest.module.content.comment.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.content.comment.dto.CreateCommentDTO;
import com.codeknest.module.content.comment.service.CommentService;
import com.codeknest.module.content.comment.vo.CommentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 评论控制器
 */
@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /** GET /api/comments?postId= — 文章评论树（公开） */
    @GetMapping
    public Result<List<CommentVO>> list(@RequestParam Long postId) {
        return Result.ok(commentService.listByPost(postId, SecurityContext.getUserId()));
    }

    /** POST /api/comments — 发表评论 / 回复 */
    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody CreateCommentDTO dto) {
        Long id = commentService.create(SecurityContext.requireUserId(), dto);
        return Result.created(Map.of("commentId", id));
    }

    /** DELETE /api/comments/{id} — 删除自己的评论（管理员可删任意） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.delete(SecurityContext.requireUserId(), id);
        return Result.ok("已删除", null);
    }

    /** POST /api/comments/{id}/like */
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable Long id) {
        int count = commentService.like(SecurityContext.requireUserId(), id);
        return Result.ok(Map.of("likeCount", count, "isLiked", true));
    }

    /** DELETE /api/comments/{id}/like */
    @DeleteMapping("/{id}/like")
    public Result<Map<String, Object>> unlike(@PathVariable Long id) {
        int count = commentService.unlike(SecurityContext.requireUserId(), id);
        return Result.ok(Map.of("likeCount", count, "isLiked", false));
    }
}
