package com.codeknest.module.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端评论列表项
 */
@Data
public class AdminCommentVO {

    private Long id;
    private String content;

    private Long postId;
    private String postTitle;

    private Long userId;
    private String username;

    private Integer likeCount;
    private Integer status;
    private LocalDateTime createdAt;
}
