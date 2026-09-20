package com.codeknest.module.content.comment.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 评论读副本同步消息生产者。
 * 若当前处于事务中则延迟到事务提交后发送，避免消费者读到未提交的数据；
 * 发送失败仅记 WARN —— MySQL 才是权威源，副本同步不得影响评论主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentSyncProducer {

    private final RabbitTemplate rabbitTemplate;

    /** 请求重建某篇文章的评论读副本（发表/删除/点赞/取消点赞统一处理） */
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
                    CommentSyncMqConfig.EXCHANGE,
                    CommentSyncMqConfig.ROUTING_KEY,
                    new CommentSyncMessage(postId)
            );
            log.debug("已发送评论副本同步消息，postId={}", postId);
        } catch (Exception e) {
            log.warn("发送评论副本同步消息失败，postId={}", postId, e);
        }
    }
}