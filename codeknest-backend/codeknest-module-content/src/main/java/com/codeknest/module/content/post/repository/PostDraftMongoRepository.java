package com.codeknest.module.content.post.repository;

import com.codeknest.module.content.post.entity.PostDraftDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 草稿 Mongo 仓储
 * 注意：放在 repository 包（而非 mapper 包），避免被 MyBatis @MapperScan 误注册
 */
public interface PostDraftMongoRepository extends MongoRepository<PostDraftDocument, String> {

    List<PostDraftDocument> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
