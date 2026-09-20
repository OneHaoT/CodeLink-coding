package com.codeknest.server.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * CodeLink 用户端 API 启动类
 * <p>
 * 扫描 com.codeknest 包下所有 Controller / Service / Component，
 * MyBatis-Plus Mapper 扫描各业务模块 mapper 包；
 * MongoDB 仓储单独扫描 repository 包（不能放 mapper 包，否则被 MyBatis 误注册）。
 */
@SpringBootApplication(scanBasePackages = "com.codeknest")
@MapperScan({
        "com.codeknest.module.account.auth.mapper",
        "com.codeknest.module.account.user.mapper",
        "com.codeknest.module.content.post.mapper",
        "com.codeknest.module.content.comment.mapper",
        "com.codeknest.module.content.interaction.mapper",
        "com.codeknest.module.account.message.mapper"
})
@EnableMongoRepositories(basePackages = {
        "com.codeknest.module.account.auth.repository",
        "com.codeknest.module.content.post.repository"
})
public class CodeknestWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeknestWebApplication.class, args);
        System.out.println("""
                
                ===============================================
                🚀  CodeLink Web API 启动成功
                ===============================================
                """);
    }
}
