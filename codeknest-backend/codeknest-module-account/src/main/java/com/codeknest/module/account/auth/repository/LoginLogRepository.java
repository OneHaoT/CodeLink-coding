package com.codeknest.module.account.auth.repository;

import com.codeknest.module.account.auth.entity.LoginLogDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 登录日志 Mongo 仓储
 * 注意：放在 repository 包（而非 mapper 包），避免被 MyBatis @MapperScan 误注册
 */
public interface LoginLogRepository extends MongoRepository<LoginLogDocument, String> {
}
