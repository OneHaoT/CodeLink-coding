package com.codeknest.module.content.comment.repository;

import com.codeknest.module.content.comment.entity.CommentDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 评论读副本 Mongo 仓储
 * 注意：放在 repository 包（而非 mapper 包），避免被 MyBatis @MapperScan 误注册
 */
public interface CommentMongoRepository extends MongoRepository<CommentDocument, String> {

    List<CommentDocument> findByPostId(Long postId);
}