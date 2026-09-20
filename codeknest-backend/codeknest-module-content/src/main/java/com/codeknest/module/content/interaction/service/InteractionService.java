package com.codeknest.module.content.interaction.service;

import com.codeknest.module.content.post.vo.PostVO;

import java.util.List;

public interface InteractionService {

    /** 点赞，返回最新点赞数 */
    int likePost(Long userId, Long postId);

    /** 取消点赞，返回最新点赞数 */
    int unlikePost(Long userId, Long postId);

    boolean isLiked(Long userId, Long postId);

    /** 收藏，返回收藏数 */
    int favoritePost(Long userId, Long postId);

    int unfavoritePost(Long userId, Long postId);

    boolean isFavorited(Long userId, Long postId);

    /** 我的收藏列表（按收藏时间倒序） */
    List<PostVO> myFavorites(Long userId);
}
