package com.codeknest.module.content.interaction.spi;

import com.codeknest.module.content.interaction.service.InteractionService;
import com.codeknest.module.content.post.spi.PostInteractionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * post 模块 SPI 实现 — 文章详情中的点赞/收藏状态查询
 */
@Service
@RequiredArgsConstructor
public class PostInteractionQueryImpl implements PostInteractionQuery {

    private final InteractionService interactionService;

    @Override
    public boolean isLiked(Long userId, Long postId) {
        return interactionService.isLiked(userId, postId);
    }

    @Override
    public boolean isFavorited(Long userId, Long postId) {
        return interactionService.isFavorited(userId, postId);
    }
}
