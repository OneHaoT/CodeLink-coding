package com.codeknest.module.content.post.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 图片删除消息生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageDeleteProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送删除临时图片消息，由消费者异步执行实际删除，不阻塞请求线程。
     */
    public void sendDelete(List<String> urls) {
        if (urls == null || urls.isEmpty()) return;
        try {
            rabbitTemplate.convertAndSend(
                    ImageMqConfig.EXCHANGE,
                    ImageMqConfig.ROUTING_KEY_DELETE,
                    new ImageDeleteMessage(urls)
            );
        } catch (Exception e) {
            log.warn("发送图片删除消息失败，urls={}", urls, e);
        }
    }
}
