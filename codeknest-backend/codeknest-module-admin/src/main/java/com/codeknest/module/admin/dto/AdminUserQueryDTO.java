package com.codeknest.module.admin.dto;

import lombok.Data;

/**
 * 管理端用户列表查询
 */
@Data
public class AdminUserQueryDTO {

    private Long page = 1L;
    private Long size = 10L;

    private String q;
    private Integer status;
}
