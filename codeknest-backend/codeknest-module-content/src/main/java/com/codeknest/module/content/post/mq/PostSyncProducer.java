package com.codeknest.module.content.post.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 文章 ES 同步消息生产者。
 * 若当前处于事务中，则延迟到事务提交后再发送，避免消费者读到未提交的数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostSyncProducer {

    private final RabbitTemplate rabbitTemplate;

    /** 请求将某篇文章同步到 ES（新增/更新/删除统一处理） */
    public void send(Long postId) {
        if (postId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(postId);
                }
            });
        } else {
            doSend(postId);
        }
    }

    private void doSend(Long postId) {
        try {
            rabbitTemplate.convertAndSend(
                    PostSyncMqConfig.EXCHANGE,
                    PostSyncMqConfig.ROUTING_KEY,
                    new PostSyncMessage(postId)
            );
            log.debug("已发送文章 ES 同步消息，postId={}", postId);
        } catch (Exception e) {
            log.warn("发送文章 ES 同步消息失败，postId={}", postId, e);
        }
    }
}
