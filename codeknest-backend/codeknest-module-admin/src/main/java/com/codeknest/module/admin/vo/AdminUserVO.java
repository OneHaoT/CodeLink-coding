package com.codeknest.module.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端用户列表项
 */
@Data
public class AdminUserVO {

    private Long id;
    private String username;
    private String email;
    private String role;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
