package com.codeknest.module.content.interaction.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.content.interaction.service.InteractionService;
import com.codeknest.module.content.post.vo.PostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文章点赞 / 收藏控制器
 */
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

    // ---------- 点赞 ----------

    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable Long id) {
        int count = interactionService.likePost(SecurityContext.requireUserId(), id);
        return Result.ok(Map.of("likeCount", count, "isLiked", true));
    }

    @DeleteMapping("/{id}/like")
    public Result<Map<String, Object>> unlike(@PathVariable Long id) {
        int count = interactionService.unlikePost(SecurityContext.requireUserId(), id);
        return Result.ok(Map.of("likeCount", count, "isLiked", false));
    }

    @GetMapping("/{id}/like/status")
    public Result<Map<String, Object>> likeStatus(@PathVariable Long id) {
        Long me = SecurityContext.getUserId();
        return Result.ok(Map.of("isLiked", me != null && interactionService.isLiked(me, id)));
    }

    // ---------- 收藏 ----------

    @PostMapping("/{id}/favorite")
    public Result<Map<String, Object>> favorite(@PathVariable Long id) {
        int count = interactionService.favoritePost(SecurityContext.requireUserId(), id);
        return Result.ok(Map.of("favoriteCount", count, "isFavorited", true));
    }

    @DeleteMapping("/{id}/favorite")
    public Result<Map<String, Object>> unfavorite(@PathVariable Long id) {
        int count = interactionService.unfavoritePost(SecurityContext.requireUserId(), id);
        return Result.ok(Map.of("favoriteCount", count, "isFavorited", false));
    }

    @GetMapping("/{id}/favorite/status")
    public Result<Map<String, Object>> favoriteStatus(@PathVariable Long id) {
        Long me = SecurityContext.getUserId();
        return Result.ok(Map.of("isFavorited", me != null && interactionService.isFavorited(me, id)));
    }

    /** GET /api/posts/favorites — 我的收藏 */
    @GetMapping("/favorites")
    public Result<List<PostVO>> favorites() {
        return Result.ok(interactionService.myFavorites(SecurityContext.requireUserId()));
    }
}
