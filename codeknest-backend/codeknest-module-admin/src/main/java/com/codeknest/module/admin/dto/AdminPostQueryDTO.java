package com.codeknest.module.admin.dto;

import lombok.Data;

/**
 * 管理端文章列表查询
 */
@Data
public class AdminPostQueryDTO {

    private Long page = 1L;
    private Long size = 10L;

    private String q;
    private Integer status;
    private Long userId;
    private Integer categoryId;
}
