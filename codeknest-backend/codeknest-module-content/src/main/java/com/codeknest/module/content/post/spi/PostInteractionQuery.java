package com.codeknest.module.content.post.spi;

/**
 * 互动状态查询 SPI — 由互动模块（interaction）实现，
 * 避免 post ↔ interaction 包循环依赖。
 * 实现 Bean 缺失时（模块未聚合）视为全部 false。
 */
public interface PostInteractionQuery {

    /** 当前用户是否已点赞文章 */
    boolean isLiked(Long userId, Long postId);

    /** 当前用户是否已收藏文章 */
    boolean isFavorited(Long userId, Long postId);
}
