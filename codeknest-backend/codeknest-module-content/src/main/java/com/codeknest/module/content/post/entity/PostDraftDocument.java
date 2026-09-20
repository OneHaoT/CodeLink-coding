package com.codeknest.module.content.post.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 草稿文档（MongoDB 集合 post_draft）
 * id 为 ObjectId 字符串；MySQL t_post_draft 自阶段 5 起弃用（表保留）
 * <p>
 * 索引由 spring.data.mongodb.auto-index-creation 在启动时幂等创建，
 * 对应 PostDraftMongoRepository.findByUserIdOrderByUpdatedAtDesc。
 */
@Data
@Document("post_draft")
@CompoundIndex(name = "idx_user_updated", def = "{'userId': 1, 'updatedAt': -1}")
public class PostDraftDocument {

    @Id
    private String id;

    private Long userId;
    private Long postId;
    private String title;
    private String content;
    private String summary;
    private String coverImage;
    /** 标签名称，逗号分隔（草稿暂存，发布时解析） */
    private String tagNames;
    private Integer categoryId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
