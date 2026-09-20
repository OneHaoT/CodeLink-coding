package com.codeknest.module.account.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.account.message.entity.Notification;
import com.codeknest.module.account.message.mapper.NotificationMapper;
import com.codeknest.module.account.message.push.NotificationPusher;
import com.codeknest.module.account.message.service.NotificationService;
import com.codeknest.module.account.message.vo.NotificationVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    /** WebSocket 推送器（仅 server-web 提供；无实现时 no-op） */
    private final ObjectProvider<NotificationPusher> pusherProvider;
    private final ObjectMapper objectMapper;

    @Override
    public void notify(Long targetUserId, String type, Long sourceUserId,
                       Long sourcePostId, Long sourceCommentId, String title, String content) {
        if (targetUserId == null) return;
        if (sourceUserId != null && sourceUserId.equals(targetUserId)) return; // 自己不通知自己

        Notification n = new Notification();
        n.setUserId(targetUserId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setSourceUserId(sourceUserId);
        n.setSourcePostId(sourcePostId);
        n.setSourceCommentId(sourceCommentId);
        n.setIsRead(0);
        notificationMapper.insert(n);
        pushRealtime(n);
    }

    /** 落库后实时推送（失败只留日志，不影响业务事务） */
    private void pushRealtime(Notification n) {
        NotificationPusher pusher = pusherProvider.getIfAvailable();
        if (pusher == null) return;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "notification");
            payload.put("bizType", n.getType());
            payload.put("title", n.getTitle());
            payload.put("content", n.getContent());
            payload.put("sourcePostId", n.getSourcePostId());
            payload.put("createdAt", LocalDateTime.now().toString());
            pusher.push(n.getUserId(), objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("通知实时推送失败，userId={}, title={}", n.getUserId(), n.getTitle(), e);
        }
    }

    @Override
    public List<NotificationVO> list(Long userId, String type) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreatedAt);
        if (StringUtils.hasText(type)) {
            wrapper.eq(Notification::getType, type);
        }
        List<Notification> list = notificationMapper.selectList(wrapper);
        if (list.isEmpty()) return Collections.emptyList();

        Set<Long> sourceIds = list.stream().map(Notification::getSourceUserId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> userMap = sourceIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(sourceIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return list.stream().map(n -> {
            NotificationVO vo = new NotificationVO();
            BeanUtils.copyProperties(n, vo);
            vo.setIsRead(n.getIsRead() != null && n.getIsRead() == 1);
            User u = n.getSourceUserId() == null ? null : userMap.get(n.getSourceUserId());
            if (u != null) {
                vo.setSourceUsername(u.getUsername());
                vo.setSourceUserAvatar(u.getAvatar());
            }
            return vo;
        }).toList();
    }

    @Override
    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    @Override
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1)
                .set(Notification::getReadAt, LocalDateTime.now()));
    }

    @Override
    public void markRead(Long userId, Long id) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1)
                .set(Notification::getReadAt, LocalDateTime.now()));
    }
}
