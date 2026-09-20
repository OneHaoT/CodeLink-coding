package com.codeknest.module.search.config;

import com.codeknest.module.search.document.PostDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * 启动时确保 ES 索引存在（幂等 createIfAbsent）。
 * <p>
 * IK 分词器由 ES 插件提供（ik_max_word / ik_smart），索引无需额外 analysis 设置。
 * 索引创建失败（ES 暂不可用）时仅打印警告，不阻断应用启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchConfig implements ApplicationRunner {

    private final ElasticsearchOperations operations;

    @Override
    public void run(ApplicationArguments args) {
        try {
            IndexOperations indexOps = operations.indexOps(PostDocument.class);
            if (indexOps.exists()) {
                log.info("ES 索引已存在: {}", PostDocument.INDEX_NAME);
                return;
            }
            indexOps.createWithMapping();
            log.info("ES 索引创建成功: {} (IK 分词)", PostDocument.INDEX_NAME);
        } catch (Exception e) {
            log.warn("创建 ES 索引 {} 失败（可能 ES 未就绪），应用继续启动: {}",
                    PostDocument.INDEX_NAME, e.getMessage());
        }
    }
}