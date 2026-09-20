package com.codeknest.module.content.comment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.content.comment.dto.CreateCommentDTO;
import com.codeknest.module.content.comment.entity.Comment;
import com.codeknest.module.content.comment.entity.CommentLike;
import com.codeknest.module.content.comment.mapper.CommentLikeMapper;
import com.codeknest.module.content.comment.mapper.CommentMapper;
import com.codeknest.module.content.comment.service.CommentService;
import com.codeknest.module.content.comment.vo.CommentVO;
import com.codeknest.module.account.message.service.NotificationService;
import com.codeknest.module.content.post.entity.Post;
import com.codeknest.module.content.post.mapper.PostMapper;
import com.codeknest.module.content.post.support.SensitiveWordChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final CommentLikeMapper likeMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final SensitiveWordChecker sensitiveWordChecker;

    @Override
    public List<CommentVO> listByPost(Long postId, Long currentUserId) {
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .eq(Comment::getStatus, 1)
                .orderByAsc(Comment::getCreatedAt)
                .orderByAsc(Comment::getId));
        if (comments.isEmpty()) return Collections.emptyList();

        Set<Long> userIds = new HashSet<>();
        comments.forEach(c -> {
            userIds.add(c.getUserId());
            if (c.getReplyToUserId() != null) userIds.add(c.getReplyToUserId());
        });
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 当前用户的点赞集合
        Set<Long> likedIds = new HashSet<>();
        if (currentUserId != null) {
            likedIds = likeMapper.selectList(new LambdaQueryWrapper<CommentLike>()
                            .eq(CommentLike::getUserId, currentUserId)
                            .in(CommentLike::getCommentId, comments.stream().map(Comment::getId).toList()))
                    .stream().map(CommentLike::getCommentId).collect(Collectors.toSet());
        }

        Map<Long, CommentVO> voMap = new LinkedHashMap<>();
        for (Comment c : comments) {
            voMap.put(c.getId(), toVO(c, userMap, likedIds));
        }

        List<CommentVO> roots = new ArrayList<>();
        for (Comment c : comments) {
            CommentVO vo = voMap.get(c.getId());
            if (c.getParentId() == null) {
                roots.add(vo);
            } else {
                CommentVO parent = voMap.get(c.getParentId());
                if (parent != null) {
                    parent.getReplies().add(vo);
                } else {
                    roots.add(vo); // 父评论被删时兜底
                }
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, CreateCommentDTO dto) {
        Post post = postMapper.selectById(dto.getPostId());
        if (post == null || post.getStatus() == null || post.getStatus() != 1) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        sensitiveWordChecker.check(dto.getContent());

        Comment comment = new Comment();
        comment.setPostId(dto.getPostId());
        comment.setUserId(userId);
        comment.setContent(dto.getContent().trim());
        comment.setLikeCount(0);
        comment.setStatus(1);

        if (dto.getParentId() != null) {
            Comment parent = commentMapper.selectById(dto.getParentId());
            if (parent == null || !parent.getPostId().equals(dto.getPostId())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "父评论不存在");
            }
            // 二级扁平化：回复子评论时挂到根评论下
            comment.setParentId(parent.getParentId() == null ? parent.getId() : parent.getParentId());
            comment.setReplyToUserId(dto.getReplyToUserId() != null ? dto.getReplyToUserId() : parent.getUserId());
        }
        commentMapper.insert(comment);

        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, post.getId())
                .setSql("comment_count = comment_count + 1"));

        // 通知
        String excerpt = comment.getContent();
        if (excerpt.length() > 50) excerpt = excerpt.substring(0, 50);
        if (comment.getParentId() == null) {
            notificationService.notify(post.getUserId(), NotificationService.COMMENT_POST,
                    userId, post.getId(), comment.getId(), null,
                    "评论了你的文章《" + post.getTitle() + "》：" + excerpt);
        } else {
            notificationService.notify(comment.getReplyToUserId(), NotificationService.REPLY_COMMENT,
                    userId, post.getId(), comment.getId(), null,
                    "回复了你的评论：" + excerpt);
        }
        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        boolean isAdmin = "ROLE_ADMIN".equals(SecurityContext.getRole());
        if (!isAdmin && !comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_OWNER);
        }
        commentMapper.deleteById(commentId); // 逻辑删除
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, comment.getPostId())
                .setSql("comment_count = GREATEST(comment_count - 1, 0)"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int like(Long userId, Long commentId) {
        Comment comment = requireComment(commentId);
        boolean exists = likeMapper.exists(new LambdaQueryWrapper<CommentLike>()
                .eq(CommentLike::getUserId, userId).eq(CommentLike::getCommentId, commentId));
        if (exists) {
            return comment.getLikeCount() == null ? 0 : comment.getLikeCount();
        }
        CommentLike like = new CommentLike();
        like.setUserId(userId);
        like.setCommentId(commentId);
        likeMapper.insert(like);
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                .eq(Comment::getId, commentId).setSql("like_count = like_count + 1"));
        notificationService.notify(comment.getUserId(), NotificationService.LIKE_COMMENT,
                userId, comment.getPostId(), commentId, null, "赞了你的评论");
        return (comment.getLikeCount() == null ? 0 : comment.getLikeCount()) + 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unlike(Long userId, Long commentId) {
        requireComment(commentId);
        int deleted = likeMapper.delete(new LambdaQueryWrapper<CommentLike>()
                .eq(CommentLike::getUserId, userId).eq(CommentLike::getCommentId, commentId));
        if (deleted > 0) {
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, commentId)
                    .setSql("like_count = GREATEST(like_count - 1, 0)"));
        }
        Comment c = commentMapper.selectById(commentId);
        return c != null && c.getLikeCount() != null ? c.getLikeCount() : 0;
    }

    // ---------- private ----------

    private Comment requireComment(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        return comment;
    }

    private CommentVO toVO(Comment c, Map<Long, User> userMap, Set<Long> likedIds) {
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setPostId(c.getPostId());
        vo.setParentId(c.getParentId());
        vo.setUserId(c.getUserId());
        vo.setContent(c.getContent());
        vo.setLikeCount(c.getLikeCount() == null ? 0 : c.getLikeCount());
        vo.setIsLiked(likedIds.contains(c.getId()));
        vo.setCreatedAt(c.getCreatedAt());

        User u = userMap.get(c.getUserId());
        if (u != null) {
            vo.setUsername(u.getUsername());
            vo.setUserAvatar(u.getAvatar());
        }
        if (c.getReplyToUserId() != null) {
            vo.setReplyToUserId(c.getReplyToUserId());
            User replyTo = userMap.get(c.getReplyToUserId());
            if (replyTo != null) vo.setReplyToUsername(replyTo.getUsername());
        }
        return vo;
    }
}
