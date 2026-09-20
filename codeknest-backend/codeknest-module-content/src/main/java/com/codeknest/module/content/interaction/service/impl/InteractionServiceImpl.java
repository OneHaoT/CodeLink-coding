package com.codeknest.module.content.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.module.content.interaction.entity.PostFavorite;
import com.codeknest.module.content.interaction.entity.PostLike;
import com.codeknest.module.content.interaction.mapper.PostFavoriteMapper;
import com.codeknest.module.content.interaction.mapper.PostLikeMapper;
import com.codeknest.module.content.interaction.service.InteractionService;
import com.codeknest.module.account.event.UserActions;
import com.codeknest.module.account.event.UserEventMessage;
import com.codeknest.module.account.event.UserEventPublisher;
import com.codeknest.module.account.message.service.NotificationService;
import com.codeknest.module.content.post.entity.Post;
import com.codeknest.module.content.post.mapper.PostMapper;
import com.codeknest.module.content.post.service.PostService;
import com.codeknest.module.content.post.vo.PostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InteractionServiceImpl implements InteractionService {

    private final PostLikeMapper likeMapper;
    private final PostFavoriteMapper favoriteMapper;
    private final PostMapper postMapper;
    private final PostService postService;
    private final NotificationService notificationService;
    private final UserEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int likePost(Long userId, Long postId) {
        Post post = requirePost(postId);
        boolean exists = likeMapper.exists(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getUserId, userId).eq(PostLike::getPostId, postId));
        if (exists) {
            return post.getLikeCount() == null ? 0 : post.getLikeCount(); // 幂等
        }
        PostLike like = new PostLike();
        like.setUserId(userId);
        like.setPostId(postId);
        likeMapper.insert(like);
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId).setSql("like_count = like_count + 1"));
        notificationService.notify(post.getUserId(), NotificationService.LIKE_POST,
                userId, postId, null, null, "赞了你的文章《" + post.getTitle() + "》");
        eventPublisher.publish(UserEventMessage
                .of(UserActions.POST_LIKE, userId, null)
                .target(UserActions.TARGET_POST, postId)
                .postSnapshot(postId, post.getTitle(), post.getSummary(), post.getCoverImage()));
        return (post.getLikeCount() == null ? 0 : post.getLikeCount()) + 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unlikePost(Long userId, Long postId) {
        int deleted = likeMapper.delete(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getUserId, userId).eq(PostLike::getPostId, postId));
        if (deleted > 0) {
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId).setSql("like_count = GREATEST(like_count - 1, 0)"));
        }
        Post post = postMapper.selectById(postId);
        return post != null && post.getLikeCount() != null ? post.getLikeCount() : 0;
    }

    @Override
    public boolean isLiked(Long userId, Long postId) {
        return likeMapper.exists(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getUserId, userId).eq(PostLike::getPostId, postId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int favoritePost(Long userId, Long postId) {
        Post post = requirePost(postId);
        boolean exists = favoriteMapper.exists(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getUserId, userId).eq(PostFavorite::getPostId, postId));
        if (exists) {
            return post.getFavoriteCount() == null ? 0 : post.getFavoriteCount();
        }
        PostFavorite fav = new PostFavorite();
        fav.setUserId(userId);
        fav.setPostId(postId);
        favoriteMapper.insert(fav);
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId).setSql("favorite_count = favorite_count + 1"));
        eventPublisher.publish(UserEventMessage
                .of(UserActions.POST_FAVORITE, userId, null)
                .target(UserActions.TARGET_POST, postId)
                .postSnapshot(postId, post.getTitle(), post.getSummary(), post.getCoverImage()));
        return (post.getFavoriteCount() == null ? 0 : post.getFavoriteCount()) + 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unfavoritePost(Long userId, Long postId) {
        int deleted = favoriteMapper.delete(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getUserId, userId).eq(PostFavorite::getPostId, postId));
        if (deleted > 0) {
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId).setSql("favorite_count = GREATEST(favorite_count - 1, 0)"));
        }
        Post post = postMapper.selectById(postId);
        return post != null && post.getFavoriteCount() != null ? post.getFavoriteCount() : 0;
    }

    @Override
    public boolean isFavorited(Long userId, Long postId) {
        return favoriteMapper.exists(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getUserId, userId).eq(PostFavorite::getPostId, postId));
    }

    @Override
    public List<PostVO> myFavorites(Long userId) {
        List<Long> ids = favoriteMapper.selectList(new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getUserId, userId)
                        .orderByDesc(PostFavorite::getCreatedAt))
                .stream().map(PostFavorite::getPostId).toList();
        return postService.listByIdsOrdered(ids, userId);
    }

    private Post requirePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }
}
