package com.codeknest.server.web.sync;

import com.codeknest.module.content.comment.mq.CommentSyncMessage;
import com.codeknest.module.content.comment.mq.CommentSyncMqConfig;
import com.codeknest.module.content.comment.service.CommentReadCopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 评论读副本同步消费者 — 按 postId 重读 MySQL 整体重建 MongoDB 副本（幂等）
 * <p>
 * 与文章 ES 同步同一范式：无论发表/删除/点赞，都按文章整体重建，消费重复投递结果一致。
 * 消费者只在 server-web（避免 web 与 admin 竞争消费）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentSyncConsumer {

    private final CommentReadCopyService commentReadCopyService;

    @RabbitListener(queues = CommentSyncMqConfig.QUEUE)
    public void onSync(CommentSyncMessage message) {
        if (message == null || message.getPostId() == null) {
            return;
        }
        log.info("消费评论读副本同步消息，postId={}", message.getPostId());
        commentReadCopyService.rebuild(message.getPostId());
    }
}