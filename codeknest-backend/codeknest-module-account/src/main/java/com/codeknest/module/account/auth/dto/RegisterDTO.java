package com.codeknest.module.account.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册 DTO — 昵称 + 密码注册（邮箱可选，后续绑定）
 */
@Data
public class RegisterDTO {

    @Email(message = "邮箱格式不正确")
    @Size(max = 128)
    private String email;

    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 32, message = "昵称长度 2-32 字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度至少 6 位")
    private String password;
}
