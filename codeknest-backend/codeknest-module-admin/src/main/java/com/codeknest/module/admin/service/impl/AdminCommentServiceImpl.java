package com.codeknest.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.admin.dto.AdminCommentQueryDTO;
import com.codeknest.module.admin.service.AdminCommentService;
import com.codeknest.module.admin.service.AuditLogService;
import com.codeknest.module.admin.support.AuditActions;
import com.codeknest.module.admin.vo.AdminCommentVO;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.content.comment.entity.Comment;
import com.codeknest.module.content.comment.mapper.CommentMapper;
import com.codeknest.module.content.comment.service.CommentService;
import com.codeknest.module.content.post.entity.Post;
import com.codeknest.module.content.post.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminCommentServiceImpl implements AdminCommentService {

    private final CommentMapper commentMapper;
    private final CommentService commentService;
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final AuditLogService auditLogService;

    @Override
    public PageVO<AdminCommentVO> page(AdminCommentQueryDTO q) {
        long size = Math.min(q.getSize() == null ? 10 : Math.max(q.getSize(), 1), 50);
        long pageNo = q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage();

        LambdaQueryWrapper<Comment> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.getQ())) {
            w.like(Comment::getContent, q.getQ().trim());
        }
        if (q.getPostId() != null) {
            w.eq(Comment::getPostId, q.getPostId());
        }
        if (q.getUserId() != null) {
            w.eq(Comment::getUserId, q.getUserId());
        }
        if (q.getStatus() != null) {
            w.eq(Comment::getStatus, q.getStatus());
        }
        w.orderByDesc(Comment::getCreatedAt).orderByDesc(Comment::getId);

        Page<Comment> result = commentMapper.selectPage(new Page<>(pageNo, size), w);
        return PageVO.of(result, enrich(result));
    }

    @Override
    public void delete(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        // 复用现有管理员删评论语义（逻辑删除 + comment_count 递减）
        commentService.delete(SecurityContext.requireUserId(), id);

        String excerpt = comment.getContent();
        if (excerpt != null && excerpt.length() > 50) excerpt = excerpt.substring(0, 50);
        auditLogService.record(AuditActions.COMMENT_DELETE, "comment:" + id, excerpt);
    }

    // ---------- private ----------

    private java.util.List<AdminCommentVO> enrich(Page<Comment> result) {
        if (result.getRecords().isEmpty()) return Collections.emptyList();

        Set<Long> userIds = new HashSet<>();
        Set<Long> postIds = new HashSet<>();
        result.getRecords().forEach(c -> {
            if (c.getUserId() != null) userIds.add(c.getUserId());
            if (c.getPostId() != null) postIds.add(c.getPostId());
        });
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Post> postMap = postIds.isEmpty() ? Collections.emptyMap()
                : postMapper.selectBatchIds(postIds).stream()
                        .collect(Collectors.toMap(Post::getId, p -> p));

        return result.getRecords().stream().map(c -> {
            AdminCommentVO vo = new AdminCommentVO();
            vo.setId(c.getId());
            vo.setContent(c.getContent());
            vo.setPostId(c.getPostId());
            Post p = postMap.get(c.getPostId());
            if (p != null) vo.setPostTitle(p.getTitle());
            vo.setUserId(c.getUserId());
            User u = userMap.get(c.getUserId());
            if (u != null) vo.setUsername(u.getUsername());
            vo.setLikeCount(c.getLikeCount());
            vo.setStatus(c.getStatus());
            vo.setCreatedAt(c.getCreatedAt());
            return vo;
        }).toList();
    }
}
