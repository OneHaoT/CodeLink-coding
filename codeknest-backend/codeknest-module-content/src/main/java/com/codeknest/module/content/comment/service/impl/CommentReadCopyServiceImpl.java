package com.codeknest.module.content.comment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.content.comment.entity.Comment;
import com.codeknest.module.content.comment.entity.CommentDocument;
import com.codeknest.module.content.comment.entity.CommentLike;
import com.codeknest.module.content.comment.mapper.CommentLikeMapper;
import com.codeknest.module.content.comment.mapper.CommentMapper;
import com.codeknest.module.content.comment.repository.CommentMongoRepository;
import com.codeknest.module.content.comment.service.CommentReadCopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论读副本服务实现。
 * <p>
 * 权威源是 MySQL t_comment：本类只负责把评论「拼装成可直接渲染的成品」写进 MongoDB，
 * 读侧命中副本即不再访问 MySQL；写入失败仅记 WARN，不影响评论主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentReadCopyServiceImpl implements CommentReadCopyService {

    private final CommentMapper commentMapper;
    private final CommentLikeMapper likeMapper;
    private final UserMapper userMapper;
    private final CommentMongoRepository commentMongoRepository;

    @Override
    public List<CommentDocument> buildFromMysql(Long postId) {
        if (postId == null) {
            return Collections.emptyList();
        }
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .eq(Comment::getStatus, 1));
        if (comments.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> userIds = new HashSet<>();
        comments.forEach(c -> {
            userIds.add(c.getUserId());
            if (c.getReplyToUserId() != null) {
                userIds.add(c.getReplyToUserId());
            }
        });
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, List<Long>> likedByComment = new HashMap<>();
        likeMapper.selectList(new LambdaQueryWrapper<CommentLike>()
                        .in(CommentLike::getCommentId, comments.stream().map(Comment::getId).toList()))
                .forEach(like -> likedByComment
                        .computeIfAbsent(like.getCommentId(), k -> new ArrayList<>())
                        .add(like.getUserId()));

        List<CommentDocument> docs = new ArrayList<>(comments.size());
        for (Comment c : comments) {
            CommentDocument d = new CommentDocument();
            d.setId(String.valueOf(c.getId()));
            d.setPostId(c.getPostId());
            d.setParentId(c.getParentId());
            d.setUserId(c.getUserId());
            d.setContent(c.getContent());
            d.setLikeCount(c.getLikeCount() == null ? 0 : c.getLikeCount());
            d.setReplyToUserId(c.getReplyToUserId());
            d.setCreatedAt(c.getCreatedAt());
            d.setLikedUserIds(likedByComment.getOrDefault(c.getId(), new ArrayList<>()));

            User author = userMap.get(c.getUserId());
            if (author != null) {
                d.setUsername(author.getUsername());
                d.setUserAvatar(author.getAvatar());
            }
            if (c.getReplyToUserId() != null) {
                User replyTo = userMap.get(c.getReplyToUserId());
                if (replyTo != null) {
                    d.setReplyToUsername(replyTo.getUsername());
                }
            }
            docs.add(d);
        }
        return docs;
    }

    @Override
    public void rebuild(Long postId) {
        if (postId == null) {
            return;
        }
        try {
            List<CommentDocument> docs = buildFromMysql(postId);
            Set<String> keepIds = docs.stream().map(CommentDocument::getId).collect(Collectors.toSet());
            // 清理副本中多出来的文档：评论被逻辑删除后 MySQL 查不到，副本需同步移除
            commentMongoRepository.findByPostId(postId).stream()
                    .filter(d -> !keepIds.contains(d.getId()))
                    .forEach(d -> commentMongoRepository.deleteById(d.getId()));
            if (!docs.isEmpty()) {
                commentMongoRepository.saveAll(docs);
            }
        } catch (Exception e) {
            log.warn("重建评论读副本失败，postId={}", postId, e);
        }
    }
}