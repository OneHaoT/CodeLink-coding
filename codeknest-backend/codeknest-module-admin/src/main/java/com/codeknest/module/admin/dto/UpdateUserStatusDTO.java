package com.codeknest.module.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户启用/禁用请求
 */
@Data
public class UpdateUserStatusDTO {

    /** 1=启用 0=禁用 */
    @NotNull(message = "状态不能为空")
    private Integer status;
}
