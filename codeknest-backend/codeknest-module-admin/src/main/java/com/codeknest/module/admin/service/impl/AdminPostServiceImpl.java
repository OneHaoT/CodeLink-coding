package com.codeknest.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.admin.dto.AdminPostQueryDTO;
import com.codeknest.module.admin.service.AdminPostService;
import com.codeknest.module.admin.service.AuditLogService;
import com.codeknest.module.admin.support.AuditActions;
import com.codeknest.module.admin.vo.AdminPostVO;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.content.post.entity.Category;
import com.codeknest.module.content.post.entity.Post;
import com.codeknest.module.content.post.mapper.CategoryMapper;
import com.codeknest.module.content.post.mapper.PostMapper;
import com.codeknest.module.content.post.mq.PostSyncProducer;
import com.codeknest.module.content.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPostServiceImpl implements AdminPostService {

    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final PostService postService;
    private final PostSyncProducer postSyncProducer;
    private final AuditLogService auditLogService;

    @Override
    public PageVO<AdminPostVO> page(AdminPostQueryDTO q) {
        long size = Math.min(q.getSize() == null ? 10 : Math.max(q.getSize(), 1), 50);
        long pageNo = q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage();

        LambdaQueryWrapper<Post> w = new LambdaQueryWrapper<>();
        if (q.getStatus() != null) {
            w.eq(Post::getStatus, q.getStatus());
        }
        if (q.getUserId() != null) {
            w.eq(Post::getUserId, q.getUserId());
        }
        if (q.getCategoryId() != null) {
            w.eq(Post::getCategoryId, q.getCategoryId());
        }
        if (StringUtils.hasText(q.getQ())) {
            String kw = q.getQ().trim();
            w.and(x -> x.like(Post::getTitle, kw).or().like(Post::getSummary, kw));
        }
        w.orderByDesc(Post::getCreatedAt).orderByDesc(Post::getId);

        Page<Post> result = postMapper.selectPage(new Page<>(pageNo, size), w);
        return PageVO.of(result, enrich(result));
    }

    @Override
    public void audit(Long id, Integer status, String reason) {
        Post post = requirePost(id);
        if (status == null || (status != 1 && status != 3)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审核状态仅支持通过(1)或拒绝(3)");
        }
        post.setStatus(status);
        if (status == 1 && post.getPublishedAt() == null) {
            post.setPublishedAt(LocalDateTime.now());
        }
        postMapper.updateById(post);

        String detail = status == 1
                ? "审核通过"
                : "审核拒绝" + (StringUtils.hasText(reason) ? "：" + reason.trim() : "");
        auditLogService.record(AuditActions.POST_AUDIT, "post:" + id, detail);
        // 审核结果影响可见性，需同步到 ES（通过则入库，拒绝则移除）
        postSyncProducer.send(id);
    }

    @Override
    public void delete(Long id) {
        requirePost(id);
        postService.deletePost(SecurityContext.requireUserId(), id);
        auditLogService.record(AuditActions.POST_DELETE, "post:" + id, "管理员删除文章");
    }

    // ---------- private ----------

    private Post requirePost(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private java.util.List<AdminPostVO> enrich(Page<Post> result) {
        if (result.getRecords().isEmpty()) return Collections.emptyList();

        Set<Long> userIds = new HashSet<>();
        Set<Long> categoryIds = new HashSet<>();
        result.getRecords().forEach(p -> {
            if (p.getUserId() != null) userIds.add(p.getUserId());
            if (p.getCategoryId() != null) categoryIds.add(p.getCategoryId().longValue());
        });
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Category> categoryMap = categoryIds.isEmpty() ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, c -> c));

        return result.getRecords().stream().map(p -> {
            AdminPostVO vo = new AdminPostVO();
            vo.setId(p.getId());
            vo.setTitle(p.getTitle());
            User u = userMap.get(p.getUserId());
            if (u != null) {
                AdminPostVO.SimpleAuthor a = new AdminPostVO.SimpleAuthor();
                a.setId(u.getId());
                a.setUsername(u.getUsername());
                vo.setAuthor(a);
            }
            if (p.getCategoryId() != null) {
                Category c = categoryMap.get(p.getCategoryId().longValue());
                if (c != null) {
                    AdminPostVO.SimpleCategory sc = new AdminPostVO.SimpleCategory();
                    sc.setId(Math.toIntExact(c.getId()));
                    sc.setName(c.getName());
                    vo.setCategory(sc);
                }
            }
            vo.setStatus(p.getStatus());
            vo.setViewCount(p.getViewCount());
            vo.setLikeCount(p.getLikeCount());
            vo.setCommentCount(p.getCommentCount());
            vo.setFavoriteCount(p.getFavoriteCount());
            vo.setCreatedAt(p.getCreatedAt());
            return vo;
        }).toList();
    }
}
