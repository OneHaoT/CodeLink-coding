package com.codeknest.module.account.actionlog.repository;

import com.codeknest.module.account.actionlog.entity.UserActionLogDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 用户操作日志 Mongo 仓储
 * 注意：放在 repository 包（而非 mapper 包），避免被 MyBatis @MapperScan 误注册
 */
public interface UserActionLogRepository extends MongoRepository<UserActionLogDocument, String> {
}