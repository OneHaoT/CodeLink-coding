package com.codeknest.module.content.comment.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论读侧副本（MongoDB 集合 comment）
 * <p>
 * MySQL t_comment 是唯一权威源；本集合把「评论 + 作者信息 + 被回复人 + 点赞集合」在同步期拼成成品，
 * 使文章评论列表一次查询即可返回，避免每次打开文章详情都跨 t_comment / t_user / t_comment_like 三张表。
 * 文档 id 即 MySQL 评论 id 的字符串形式，可随时从 MySQL 重放重建。
 * <p>
 * 索引由 spring.data.mongodb.auto-index-creation 在启动时幂等创建。
 */
@Data
@Document("comment")
@CompoundIndex(name = "idx_post_created", def = "{'postId': 1, 'createdAt': 1}")
public class CommentDocument {

    /** MySQL 评论 id 的字符串形式 */
    @Id
    private String id;

    private Long postId;
    private Long parentId;

    private Long userId;
    private String username;
    private String userAvatar;

    private Long replyToUserId;
    private String replyToUsername;

    private String content;
    private Integer likeCount;
    /** 点赞者 id 集合，读时直接判定 isLiked，免去点赞表点查 */
    private List<Long> likedUserIds = new ArrayList<>();

    private LocalDateTime createdAt;
}