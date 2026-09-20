package com.codeknest.module.content.post.mq;

import com.codeknest.module.content.post.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 图片删除消息消费者 — 异步执行实际文件删除
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageDeleteConsumer {

    private final ImageStorageService imageStorageService;

    @RabbitListener(queues = ImageMqConfig.QUEUE_DELETE)
    public void onDelete(ImageDeleteMessage message) {
        if (message == null || message.getUrls() == null) return;
        log.debug("消费图片删除消息，数量={}", message.getUrls().size());
        imageStorageService.doDeleteTemp(message.getUrls());
    }
}
