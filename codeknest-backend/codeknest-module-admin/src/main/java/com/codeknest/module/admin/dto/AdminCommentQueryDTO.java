package com.codeknest.module.admin.dto;

import lombok.Data;

/**
 * 管理端评论列表查询
 */
@Data
public class AdminCommentQueryDTO {

    private Long page = 1L;
    private Long size = 10L;

    private String q;
    private Long postId;
    private Long userId;
    private Integer status;
}
