package com.codeknest.server.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * CodeLink 管理后台 API 启动类
 * <p>
 * 预留独立部署端口（默认 8081），后期可拆分为独立微服务。
 * MongoDB 仓储单独扫描 repository 包（不能放 mapper 包，否则被 MyBatis 误注册）。
 */
@SpringBootApplication(scanBasePackages = "com.codeknest")
@MapperScan({
        "com.codeknest.module.account.auth.mapper",
        "com.codeknest.module.account.user.mapper",
        "com.codeknest.module.content.post.mapper",
        "com.codeknest.module.content.comment.mapper",
        "com.codeknest.module.content.interaction.mapper",
        "com.codeknest.module.account.message.mapper",
        "com.codeknest.module.admin.mapper"
})
@EnableMongoRepositories(basePackages = {
        "com.codeknest.module.account.actionlog.repository",
        "com.codeknest.module.account.activity.repository",
        "com.codeknest.module.content.post.repository",
        "com.codeknest.module.content.comment.repository"
})
public class CodeknestAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeknestAdminApplication.class, args);
        System.out.println("""
                
                ===============================================
                🛠️  CodeLink Admin API 启动成功
                ===============================================
                """);
    }
}
