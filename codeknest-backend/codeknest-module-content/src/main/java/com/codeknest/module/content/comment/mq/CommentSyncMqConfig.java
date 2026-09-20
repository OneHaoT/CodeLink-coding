package com.codeknest.module.content.comment.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 评论 → MongoDB 读副本同步 MQ 配置
 * 单队列单路由：消费端按 postId 重读 MySQL，整体重建该文章的评论副本（幂等）。
 * 生产端：两个 starter 都会声明队列（admin 删除评论时也需投递）；消费端只在 server-web。
 */
@Configuration
public class CommentSyncMqConfig {

    public static final String EXCHANGE = "comment.sync.exchange";
    public static final String QUEUE = "comment.sync.queue";
    public static final String ROUTING_KEY = "comment.sync";

    @Bean
    public TopicExchange commentSyncExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue commentSyncQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding commentSyncBinding() {
        return BindingBuilder.bind(commentSyncQueue()).to(commentSyncExchange()).with(ROUTING_KEY);
    }
}