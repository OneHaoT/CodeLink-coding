package com.codeknest.module.account.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录 DTO — account 可以是邮箱或昵称
 */
@Data
public class LoginDTO {

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;
}
