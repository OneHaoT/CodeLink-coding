package com.codeknest.module.content.post.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图片相关 MQ 配置
 */
@Configuration
public class ImageMqConfig {

    public static final String EXCHANGE = "image.exchange";
    public static final String QUEUE_DELETE = "image.delete.queue";
    public static final String ROUTING_KEY_DELETE = "image.delete";

    /** 使用 JSON 序列化，避免 Java 原生序列化的安全白名单问题 */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange imageExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue imageDeleteQueue() {
        return new Queue(QUEUE_DELETE, true);
    }

    @Bean
    public Binding imageDeleteBinding() {
        return BindingBuilder.bind(imageDeleteQueue()).to(imageExchange()).with(ROUTING_KEY_DELETE);
    }
}
