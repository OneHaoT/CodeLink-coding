package com.codeknest.module.search.mq;

import com.codeknest.module.content.post.mq.PostSyncMessage;
import com.codeknest.module.search.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 文章同步消息消费者单元测试 — 纯 Mockito
 */
@ExtendWith(MockitoExtension.class)
class PostSyncConsumerTest {

    @Mock
    private SearchService searchService;

    private PostSyncConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PostSyncConsumer(searchService);
    }

    @Test
    @DisplayName("空消息 — 忽略不处理")
    void onSync_nullMessage_ignored() {
        consumer.onSync(null);
        verifyNoInteractions(searchService);
    }

    @Test
    @DisplayName("消息缺 postId — 忽略不处理")
    void onSync_missingPostId_ignored() {
        consumer.onSync(new PostSyncMessage(null));
        verifyNoInteractions(searchService);
    }

    @Test
    @DisplayName("正常消息 — 委托 syncPost 重新读取 MySQL 决定写入或删除（幂等）")
    void onSync_delegatesToSyncPost() {
        consumer.onSync(new PostSyncMessage(77L));
        verify(searchService).syncPost(77L);
    }
}
