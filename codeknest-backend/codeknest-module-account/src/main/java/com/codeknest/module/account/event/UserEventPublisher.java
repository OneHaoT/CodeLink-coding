package com.codeknest.module.account.event;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 用户事件发布器。
 * <p>
 * 写路径原则：MySQL 是唯一权威源，事件一律在事务提交后异步投递，投递失败只记 WARN，
 * 绝不回滚业务事务 —— 同步链路不因 MongoDB / RabbitMQ 不可用而受影响。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(UserEventMessage message) {
        if (message == null) {
            return;
        }
        if (message.getEventId() == null) {
            message.setEventId(java.util.UUID.randomUUID().toString());
        }
        fillRequestInfo(message);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(message);
                }
            });
        } else {
            doSend(message);
        }
    }

    private void doSend(UserEventMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    UserEventMqConfig.EXCHANGE,
                    UserEventMqConfig.ROUTING_KEY,
                    message
            );
            log.debug("已发送用户事件，action={}, eventId={}", message.getAction(), message.getEventId());
        } catch (Exception e) {
            log.warn("发送用户事件失败，action={}, eventId={}", message.getAction(), message.getEventId(), e);
        }
    }

    /** 请求上下文只在线程内有效，必须在投递前取好（afterCommit 时机已不可靠） */
    private void fillRequestInfo(UserEventMessage message) {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return;
        }
        if (!StringUtils.hasText(message.getIp())) {
            message.setIp(req.getRemoteAddr());
        }
        if (!StringUtils.hasText(message.getUserAgent())) {
            message.setUserAgent(req.getHeader("User-Agent"));
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }
}