package com.codeknest.module.account.actionlog.service.impl;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.account.actionlog.entity.UserActionLogDocument;
import com.codeknest.module.account.actionlog.repository.UserActionLogRepository;
import com.codeknest.module.account.actionlog.service.UserActionLogService;
import com.codeknest.module.account.actionlog.vo.UserActionLogVO;
import com.codeknest.module.account.event.UserActions;
import com.codeknest.module.account.event.UserEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 通用用户操作日志服务实现 — MongoDB 存储；写入失败仅记日志，不影响业务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserActionLogServiceImpl implements UserActionLogService {

    private final UserActionLogRepository repository;
    private final MongoTemplate mongoTemplate;

    @Override
    public void save(UserEventMessage message) {
        if (message == null || message.getEventId() == null) {
            return;
        }
        try {
            UserActionLogDocument doc = new UserActionLogDocument();
            doc.setId(message.getEventId());
            doc.setUserId(message.getUserId());
            doc.setAccount(message.getAccount());
            doc.setAction(message.getAction());
            doc.setTargetType(message.getTargetType());
            doc.setTargetId(message.getTargetId());
            doc.setDetail(describe(message));
            doc.setSuccess(message.getSuccess());
            doc.setFailReason(message.getFailReason());
            doc.setIp(message.getIp());
            doc.setUserAgent(message.getUserAgent());
            doc.setCreatedAt(message.getOccurredAt() == null ? LocalDateTime.now() : message.getOccurredAt());
            repository.save(doc);
        } catch (Exception e) {
            log.warn("写入操作日志失败，action={}, eventId={}", message.getAction(), message.getEventId(), e);
        }
    }

    @Override
    public PageVO<UserActionLogVO> page(int page, int size, String action, String account,
                                        Long userId, LocalDate start, LocalDate end) {
        int pageNo = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);

        Criteria criteria = new Criteria();
        if (StringUtils.hasText(action)) {
            criteria.and("action").is(action.trim());
        }
        if (StringUtils.hasText(account)) {
            criteria.and("account").regex(Pattern.compile(Pattern.quote(account.trim()), Pattern.CASE_INSENSITIVE));
        }
        if (userId != null) {
            criteria.and("userId").is(userId);
        }
        if (start != null || end != null) {
            Criteria time = criteria.and("createdAt");
            if (start != null) {
                time.gte(start.atStartOfDay());
            }
            if (end != null) {
                time.lte(end.atTime(23, 59, 59));
            }
        }

        Query query = Query.query(criteria)
                .with(PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        long total = mongoTemplate.count(Query.query(criteria), UserActionLogDocument.class);
        List<UserActionLogVO> items = mongoTemplate.find(query, UserActionLogDocument.class).stream()
                .map(d -> UserActionLogVO.builder()
                        .id(d.getId())
                        .userId(d.getUserId())
                        .account(d.getAccount())
                        .action(d.getAction())
                        .targetType(d.getTargetType())
                        .targetId(d.getTargetId())
                        .detail(d.getDetail())
                        .success(d.getSuccess())
                        .failReason(d.getFailReason())
                        .ip(d.getIp())
                        .userAgent(d.getUserAgent())
                        .createdAt(d.getCreatedAt())
                        .build())
                .toList();

        PageVO<UserActionLogVO> vo = new PageVO<>();
        vo.setItems(items);
        vo.setPage(pageNo);
        vo.setSize(pageSize);
        vo.setTotal(total);
        vo.setTotalPages((total + pageSize - 1) / pageSize);
        return vo;
    }

    /** 生成可读描述，避免后台列表只看到动作码 */
    private String describe(UserEventMessage m) {
        String action = m.getAction();
        if (action == null) {
            return null;
        }
        return switch (action) {
            case UserActions.LOGIN_SUCCESS -> "登录成功";
            case UserActions.LOGIN_FAIL -> "登录失败：" + (m.getFailReason() == null ? "未知原因" : m.getFailReason());
            case UserActions.LOGOUT -> "退出登录";
            case UserActions.REGISTER -> "注册账号";
            case UserActions.POST_PUBLISH -> "发表了文章《" + safe(m.getPostTitle()) + "》";
            case UserActions.POST_DELETE -> "删除了文章《" + safe(m.getPostTitle()) + "》";
            case UserActions.COMMENT_CREATE -> "评论了文章《" + safe(m.getPostTitle())
                    + "》：" + safe(m.getCommentExcerpt());
            case UserActions.COMMENT_DELETE -> "删除了评论：" + safe(m.getCommentExcerpt());
            case UserActions.FOLLOW -> "关注了 " + safe(m.getTargetUsername());
            case UserActions.UNFOLLOW -> "取消关注 " + safe(m.getTargetUsername());
            case UserActions.POST_LIKE -> "点赞了文章《" + safe(m.getPostTitle()) + "》";
            case UserActions.POST_FAVORITE -> "收藏了文章《" + safe(m.getPostTitle()) + "》";
            default -> action;
        };
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}