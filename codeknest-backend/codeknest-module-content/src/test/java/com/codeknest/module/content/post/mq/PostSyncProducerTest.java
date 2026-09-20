package com.codeknest.module.content.post.mq;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ES 同步消息生产者单元测试 — 验证事务内延迟到 afterCommit 再发送
 */
@ExtendWith(MockitoExtension.class)
class PostSyncProducerTest {

    private static final Long POST_ID = 77L;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private PostSyncProducer producer;

    @BeforeEach
    void setUp() {
        producer = new PostSyncProducer(rabbitTemplate);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    @DisplayName("无事务 — 立即发送同步消息")
    void send_withoutTransaction_sendsImmediately() {
        producer.send(POST_ID);

        verify(rabbitTemplate).convertAndSend(
                PostSyncMqConfig.EXCHANGE, PostSyncMqConfig.ROUTING_KEY, new PostSyncMessage(POST_ID));
    }

    @Test
    @DisplayName("postId 为空 — 不发送")
    void send_nullId_ignored() {
        producer.send(null);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("事务中 — 提交前不发送，afterCommit 后才发送")
    void send_withinTransaction_defersToAfterCommit() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        producer.send(POST_ID);

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }

        verify(rabbitTemplate).convertAndSend(
                PostSyncMqConfig.EXCHANGE, PostSyncMqConfig.ROUTING_KEY, new PostSyncMessage(POST_ID));
    }
}
