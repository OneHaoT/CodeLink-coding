package com.codeknest.module.account.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.codeknest.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户账号表 — 对应 t_user
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BaseEntity {

    /** 登录邮箱（唯一） */
    @TableField("email")
    private String email;

    /** BCrypt 加密后的密码 */
    @TableField("password")
    private String password;

    /** 显示昵称（唯一） */
    @TableField("username")
    private String username;

    /** 头像 URL */
    @TableField("avatar")
    private String avatar;

    /** 状态 1=正常 0=禁用 */
    @TableField("status")
    private Integer status;

    /** 角色 ROLE_USER / ROLE_MODERATOR / ROLE_ADMIN */
    @TableField("role")
    private String role;

    /** 最后登录时间 */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;
}
