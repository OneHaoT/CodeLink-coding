package com.codeknest.module.content.comment.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论读副本同步消息 — 只传 postId，消费端重读 MySQL 后整体重建该文章的评论副本（幂等）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentSyncMessage {

    private Long postId;
}