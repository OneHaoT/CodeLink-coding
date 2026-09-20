package com.codeknest.module.account.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 用户事件发布器单元测试 — 事件 id 兜底、请求信息采集、事务内延迟投递与投递失败不影响主流程
 */
@ExtendWith(MockitoExtension.class)
class UserEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private UserEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new UserEventPublisher(rabbitTemplate);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    @DisplayName("无事务 — 立即投递，并采集 IP 与 User-Agent")
    void publish_withoutTransaction_sendsImmediately() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");
        request.addHeader("User-Agent", "JUnit-Agent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        UserEventMessage message = UserEventMessage.of(UserActions.LOGIN_SUCCESS, 5L, "admin");
        message.setEventId(null);

        publisher.publish(message);

        ArgumentCaptor<UserEventMessage> captor = ArgumentCaptor.forClass(UserEventMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(UserEventMqConfig.EXCHANGE), eq(UserEventMqConfig.ROUTING_KEY), captor.capture());
        UserEventMessage sent = captor.getValue();
        assertThat(sent.getEventId()).isNotBlank();
        assertThat(sent.getIp()).isEqualTo("192.168.1.10");
        assertThat(sent.getUserAgent()).isEqualTo("JUnit-Agent");
    }

    @Test
    @DisplayName("事务中 — 提交前不投递，afterCommit 后才投递")
    void publish_withinTransaction_defersToAfterCommit() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(UserEventMessage.of(UserActions.POST_PUBLISH, 5L, "admin"));

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        verify(rabbitTemplate).convertAndSend(
                anyString(), anyString(), any(UserEventMessage.class));
    }

    @Test
    @DisplayName("投递失败 — 只记 WARN，不把异常抛回业务事务")
    void publish_sendFailure_doesNotThrow() {
        doThrow(new RuntimeException("mq down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        publisher.publish(UserEventMessage.of(UserActions.POST_DELETE, 5L, "admin"));
        // 不抛异常即通过
    }

    @Test
    @DisplayName("消息为空 — 直接跳过，不投递")
    void publish_null_ignored() {
        publisher.publish(null);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}