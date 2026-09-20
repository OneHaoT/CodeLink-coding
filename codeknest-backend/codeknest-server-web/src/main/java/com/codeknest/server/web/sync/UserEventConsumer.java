package com.codeknest.server.web.sync;

import com.codeknest.module.account.actionlog.service.UserActionLogService;
import com.codeknest.module.account.activity.service.UserActivityService;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.account.event.UserActions;
import com.codeknest.module.account.event.UserEventMessage;
import com.codeknest.module.account.event.UserEventMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 用户事件消费者 — 一份消息写入两份读侧投影（幂等，_id = eventId）
 * <p>
 * 1) 操作日志：全部 12 类动作都记录（含登录成败、IP、UA）
 * 2) 用户动态：仅 feed 类动作（发文/评论/关注/点赞/收藏）写入动态流
 * <p>
 * 消费者只在 server-web（避免 web 与 admin 竞争消费，与 post.sync.queue 约定一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final UserActionLogService userActionLogService;
    private final UserActivityService userActivityService;
    private final UserMapper userMapper;

    @RabbitListener(queues = UserEventMqConfig.QUEUE)
    public void onEvent(UserEventMessage message) {
        if (message == null || message.getEventId() == null) {
            return;
        }
        log.info("消费用户事件，action={}, userId={}", message.getAction(), message.getUserId());
        fillAccount(message);
        userActionLogService.save(message);
        if (UserActions.isFeedAction(message.getAction())) {
            userActivityService.save(message);
        }
    }

    /** 非登录类事件生产侧不带账号，异步补一次昵称，保证后台按账号筛选可用 */
    private void fillAccount(UserEventMessage message) {
        if (StringUtils.hasText(message.getAccount()) || message.getUserId() == null) {
            return;
        }
        User user = userMapper.selectById(message.getUserId());
        if (user != null) {
            message.setAccount(user.getUsername());
        }
    }
}