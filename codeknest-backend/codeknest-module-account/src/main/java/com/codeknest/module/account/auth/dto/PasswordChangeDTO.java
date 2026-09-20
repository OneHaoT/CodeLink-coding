package com.codeknest.module.account.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码 DTO
 */
@Data
public class PasswordChangeDTO {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度 8-128 字符")
    private String newPassword;
}
