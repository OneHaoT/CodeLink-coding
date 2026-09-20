package com.codeknest.module.content.post.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文章 → Elasticsearch 同步 MQ 配置
 * 单队列单路由：消费端按 postId 重新读取 MySQL，决定写入或删除索引文档（幂等）。
 */
@Configuration
public class PostSyncMqConfig {

    public static final String EXCHANGE = "post.sync.exchange";
    public static final String QUEUE = "post.sync.queue";
    public static final String ROUTING_KEY = "post.sync";

    @Bean
    public TopicExchange postSyncExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue postSyncQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding postSyncBinding() {
        return BindingBuilder.bind(postSyncQueue()).to(postSyncExchange()).with(ROUTING_KEY);
    }
}
