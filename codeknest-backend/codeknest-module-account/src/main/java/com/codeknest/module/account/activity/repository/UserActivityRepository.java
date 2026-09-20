package com.codeknest.module.account.activity.repository;

import com.codeknest.module.account.activity.entity.UserActivityDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 用户动态 Mongo 仓储
 * 注意：放在 repository 包（而非 mapper 包），避免被 MyBatis @MapperScan 误注册
 */
public interface UserActivityRepository extends MongoRepository<UserActivityDocument, String> {
}