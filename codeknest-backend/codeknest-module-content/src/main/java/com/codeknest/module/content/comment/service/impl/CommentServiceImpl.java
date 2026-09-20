package com.codeknest.module.content.comment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.account.event.UserActions;
import com.codeknest.module.account.event.UserEventMessage;
import com.codeknest.module.account.event.UserEventPublisher;
import com.codeknest.module.account.message.service.NotificationService;
import com.codeknest.module.content.comment.dto.CreateCommentDTO;
import com.codeknest.module.content.comment.entity.Comment;
import com.codeknest.module.content.comment.entity.CommentDocument;
import com.codeknest.module.content.comment.entity.CommentLike;
import com.codeknest.module.content.comment.mapper.CommentLikeMapper;
import com.codeknest.module.content.comment.mapper.CommentMapper;
import com.codeknest.module.content.comment.mq.CommentSyncProducer;
import com.codeknest.module.content.comment.repository.CommentMongoRepository;
import com.codeknest.module.content.comment.service.CommentReadCopyService;
import com.codeknest.module.content.comment.service.CommentService;
import com.codeknest.module.content.comment.vo.CommentVO;
import com.codeknest.module.content.post.entity.Post;
import com.codeknest.module.content.post.mapper.PostMapper;
import com.codeknest.module.content.post.support.SensitiveWordChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final CommentLikeMapper likeMapper;
    private final PostMapper postMapper;
    private final NotificationService notificationService;
    private final SensitiveWordChecker sensitiveWordChecker;
    private final CommentReadCopyService commentReadCopyService;
    private final CommentMongoRepository commentMongoRepository;
    private final CommentSyncProducer commentSyncProducer;
    private final UserEventPublisher eventPublisher;

    /**
     * 文章评论树。
     * 读路径前置：先读 MongoDB 读副本（一次查询直接出结果），未命中回源 MySQL 并异步回填，
     * MongoDB 不可用时同样回源 MySQL —— 副本只是关口，不能成为功能依赖。
     */
    @Override
    public List<CommentVO> listByPost(Long postId, Long currentUserId) {
        List<CommentDocument> cached = readCopy(postId);
        if (cached != null && !cached.isEmpty()) {
            return assembleTree(cached, currentUserId);
        }

        List<CommentDocument> fresh = commentReadCopyService.buildFromMysql(postId);
        if (fresh.isEmpty()) {
            return Collections.emptyList();
        }
        if (cached != null) {
            // 仅真正的「未命中」才补建副本；MongoDB 不可用时补建消息会被消费者反复重投，故跳过
            commentSyncProducer.send(postId);
        }
        return assembleTree(fresh, currentUserId);
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
        String excerpt = excerpt(comment.getContent());
        if (comment.getParentId() == null) {
            notificationService.notify(post.getUserId(), NotificationService.COMMENT_POST,
                    userId, post.getId(), comment.getId(), null,
                    "评论了你的文章《" + post.getTitle() + "》：" + excerpt);
        } else {
            notificationService.notify(comment.getReplyToUserId(), NotificationService.REPLY_COMMENT,
                    userId, post.getId(), comment.getId(), null,
                    "回复了你的评论：" + excerpt);
        }

        // 读副本同步 + 用户事件（均异步，失败不影响评论主流程）
        commentSyncProducer.send(post.getId());
        eventPublisher.publish(UserEventMessage
                .of(UserActions.COMMENT_CREATE, userId, null)
                .target(UserActions.TARGET_COMMENT, comment.getId())
                .postSnapshot(post.getId(), post.getTitle(), post.getSummary(), post.getCoverImage())
                .commentExcerpt(excerpt));
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

        commentSyncProducer.send(comment.getPostId());
        eventPublisher.publish(UserEventMessage
                .of(UserActions.COMMENT_DELETE, userId, null)
                .target(UserActions.TARGET_COMMENT, commentId)
                .commentExcerpt(excerpt(comment.getContent())));
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
        commentSyncProducer.send(comment.getPostId());
        return (comment.getLikeCount() == null ? 0 : comment.getLikeCount()) + 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unlike(Long userId, Long commentId) {
        Comment comment = requireComment(commentId);
        int deleted = likeMapper.delete(new LambdaQueryWrapper<CommentLike>()
                .eq(CommentLike::getUserId, userId).eq(CommentLike::getCommentId, commentId));
        if (deleted > 0) {
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, commentId)
                    .setSql("like_count = GREATEST(like_count - 1, 0)"));
            commentSyncProducer.send(comment.getPostId());
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

    private String excerpt(String content) {
        if (content == null) {
            return null;
        }
        return content.length() > 50 ? content.substring(0, 50) : content;
    }

    /** 读 MongoDB 读副本；不可用时返回 null（与「未命中返回空列表」区分）交由回源逻辑兜底 */
    private List<CommentDocument> readCopy(Long postId) {
        try {
            return commentMongoRepository.findByPostId(postId);
        } catch (Exception e) {
            log.warn("读取评论读副本失败，回源 MySQL，postId={}", postId, e);
            return null;
        }
    }

    /** 由读副本文档拼装二级评论树（副本与回源共用同一套拼装逻辑） */
    private List<CommentVO> assembleTree(List<CommentDocument> docs, Long currentUserId) {
        List<CommentDocument> sorted = new ArrayList<>(docs);
        sorted.sort(Comparator
                .comparing(CommentDocument::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(d -> d.getId() == null ? 0L : Long.parseLong(d.getId())));

        Map<Long, CommentVO> voMap = new LinkedHashMap<>();
        for (CommentDocument d : sorted) {
            CommentVO vo = new CommentVO();
            vo.setId(Long.valueOf(d.getId()));
            vo.setPostId(d.getPostId());
            vo.setParentId(d.getParentId());
            vo.setUserId(d.getUserId());
            vo.setUsername(d.getUsername());
            vo.setUserAvatar(d.getUserAvatar());
            vo.setReplyToUserId(d.getReplyToUserId());
            vo.setReplyToUsername(d.getReplyToUsername());
            vo.setContent(d.getContent());
            vo.setLikeCount(d.getLikeCount() == null ? 0 : d.getLikeCount());
            vo.setIsLiked(currentUserId != null && d.getLikedUserIds() != null
                    && d.getLikedUserIds().contains(currentUserId));
            vo.setCreatedAt(d.getCreatedAt());
            voMap.put(vo.getId(), vo);
        }

        List<CommentVO> roots = new ArrayList<>();
        for (CommentDocument d : sorted) {
            CommentVO vo = voMap.get(Long.valueOf(d.getId()));
            if (d.getParentId() == null) {
                roots.add(vo);
            } else {
                CommentVO parent = voMap.get(d.getParentId());
                if (parent != null) {
                    parent.getReplies().add(vo);
                } else {
                    roots.add(vo); // 父评论被删时兜底
                }
            }
        }
        return roots;
    }
}