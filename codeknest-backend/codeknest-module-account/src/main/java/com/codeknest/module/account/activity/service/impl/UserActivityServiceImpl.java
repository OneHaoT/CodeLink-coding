package com.codeknest.module.account.activity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.account.activity.entity.UserActivityDocument;
import com.codeknest.module.account.activity.repository.UserActivityRepository;
import com.codeknest.module.account.activity.service.UserActivityService;
import com.codeknest.module.account.activity.vo.UserActivityVO;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.account.event.UserEventMessage;
import com.codeknest.module.account.user.entity.UserFollow;
import com.codeknest.module.account.user.mapper.UserFollowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 用户动态服务实现 — MongoDB 存储。
 * <p>
 * 读侧降级：MongoDB 不可用时返回空列表并记 WARN，接口不报错（动态是读侧投影，不得影响主链路）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityServiceImpl implements UserActivityService {

    private final UserActivityRepository repository;
    private final MongoTemplate mongoTemplate;
    private final UserMapper userMapper;
    private final UserFollowMapper followMapper;

    @Override
    public void save(UserEventMessage message) {
        if (message == null || message.getEventId() == null) {
            return;
        }
        try {
            UserActivityDocument doc = new UserActivityDocument();
            doc.setId(message.getEventId());
            doc.setUserId(message.getUserId());

            User actor = message.getUserId() == null ? null : userMapper.selectById(message.getUserId());
            if (actor != null) {
                doc.setActorUsername(actor.getUsername());
                doc.setActorAvatar(actor.getAvatar());
            }

            doc.setAction(message.getAction());
            doc.setTargetType(message.getTargetType());
            doc.setTargetId(message.getTargetId());
            doc.setPostId(message.getPostId());
            doc.setPostTitle(message.getPostTitle());
            doc.setPostSummary(message.getPostSummary());
            doc.setPostCoverImage(message.getPostCoverImage());
            doc.setCommentExcerpt(message.getCommentExcerpt());
            doc.setTargetUsername(message.getTargetUsername());
            doc.setCreatedAt(message.getOccurredAt() == null ? LocalDateTime.now() : message.getOccurredAt());
            repository.save(doc);
        } catch (Exception e) {
            log.warn("写入用户动态失败，action={}, eventId={}", message.getAction(), message.getEventId(), e);
        }
    }

    @Override
    public PageVO<UserActivityVO> pageByUser(Long userId, int page, int size) {
        if (userId == null) {
            return empty(page, size);
        }
        Criteria criteria = new Criteria().and("userId").is(userId);
        return query(criteria, page, size);
    }

    @Override
    public PageVO<UserActivityVO> pageFollowing(Long currentUserId, int page, int size) {
        if (currentUserId == null) {
            return empty(page, size);
        }
        // 关注关系实时取自 MySQL：取关后对方动态立即从关注流消失
        List<Long> ids = new ArrayList<>();
        ids.add(currentUserId);
        followMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, currentUserId))
                .forEach(rel -> ids.add(rel.getFollowingId()));

        Criteria criteria = new Criteria().and("userId").in(ids);
        return query(criteria, page, size);
    }

    // ---------- private ----------

    private PageVO<UserActivityVO> query(Criteria criteria, int page, int size) {
        int pageNo = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 50);
        try {
            Query query = Query.query(criteria)
                    .with(PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
            long total = mongoTemplate.count(Query.query(criteria), UserActivityDocument.class);
            List<UserActivityVO> items = mongoTemplate.find(query, UserActivityDocument.class).stream()
                    .map(this::toVO).toList();

            PageVO<UserActivityVO> vo = new PageVO<>();
            vo.setItems(items);
            vo.setPage(pageNo);
            vo.setSize(pageSize);
            vo.setTotal(total);
            vo.setTotalPages((total + pageSize - 1) / pageSize);
            return vo;
        } catch (Exception e) {
            log.warn("查询用户动态失败，降级为空列表", e);
            return empty(pageNo, pageSize);
        }
    }

    private PageVO<UserActivityVO> empty(int page, int size) {
        PageVO<UserActivityVO> vo = new PageVO<>();
        vo.setItems(Collections.emptyList());
        vo.setPage(Math.max(page, 1));
        vo.setSize(Math.min(Math.max(size, 1), 50));
        vo.setTotal(0L);
        vo.setTotalPages(0L);
        return vo;
    }

    private UserActivityVO toVO(UserActivityDocument d) {
        return UserActivityVO.builder()
                .id(d.getId())
                .userId(d.getUserId())
                .actorUsername(d.getActorUsername())
                .actorAvatar(d.getActorAvatar())
                .action(d.getAction())
                .targetType(d.getTargetType())
                .targetId(d.getTargetId())
                .postId(d.getPostId())
                .postTitle(d.getPostTitle())
                .postSummary(d.getPostSummary())
                .postCoverImage(d.getPostCoverImage())
                .commentExcerpt(d.getCommentExcerpt())
                .targetUsername(d.getTargetUsername())
                .createdAt(d.getCreatedAt())
                .build();
    }
}