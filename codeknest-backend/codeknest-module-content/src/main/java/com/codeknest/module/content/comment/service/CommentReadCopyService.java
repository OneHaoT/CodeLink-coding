package com.codeknest.module.content.comment.service;

import com.codeknest.module.content.comment.entity.CommentDocument;

import java.util.List;

/**
 * 评论读副本服务 — 维护 MongoDB comment 集合（MySQL 为唯一权威源）
 */
public interface CommentReadCopyService {

    /**
     * 按 MySQL 现场数据构造读副本文档（含作者、被回复人、点赞集合）。
     * 也用于读路径回源时直接复用，避免回源逻辑与同步逻辑各写一套。
     */
    List<CommentDocument> buildFromMysql(Long postId);

    /** 从 MySQL 整体重建某篇文章的评论读副本：upsert 全量 + 清理已不存在的文档（幂等，供消费端调用） */
    void rebuild(Long postId);
}