package com.codeknest.module.account.event;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户事件 MQ 配置（登录/登出/注册/发文/评论/关注/点赞…）
 * <p>
 * 生产端：两个 starter 都会声明队列（admin 需要向同一队列投递）。
 * 消费端：只在 server-web（避免 web 与 admin 竞争消费，与 post.sync.queue 约定一致）。
 */
@Configuration
public class UserEventMqConfig {

    public static final String EXCHANGE = "user.event.exchange";
    public static final String QUEUE = "user.event.queue";
    public static final String ROUTING_KEY = "user.event";

    @Bean
    public TopicExchange userEventExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue userEventQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding userEventBinding() {
        return BindingBuilder.bind(userEventQueue()).to(userEventExchange()).with(ROUTING_KEY);
    }
}