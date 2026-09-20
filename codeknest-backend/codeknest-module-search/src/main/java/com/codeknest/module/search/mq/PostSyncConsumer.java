package com.codeknest.module.search.mq;

import com.codeknest.module.content.post.mq.PostSyncMessage;
import com.codeknest.module.content.post.mq.PostSyncMqConfig;
import com.codeknest.module.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 文章同步消息消费者 — 按 postId 重新读取 MySQL 决定写入或删除索引文档（幂等）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostSyncConsumer {

    private final SearchService searchService;

    @RabbitListener(queues = PostSyncMqConfig.QUEUE)
    public void onSync(PostSyncMessage message) {
        if (message == null || message.getPostId() == null) return;
        log.info("消费文章 ES 同步消息，postId={}", message.getPostId());
        searchService.syncPost(message.getPostId());
    }
}
