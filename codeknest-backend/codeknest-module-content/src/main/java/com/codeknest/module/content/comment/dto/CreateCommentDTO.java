package com.codeknest.module.content.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表评论 / 回复 DTO
 */
@Data
public class CreateCommentDTO {

    @NotNull(message = "文章 ID 不能为空")
    private Long postId;

    /** 父评论 ID；顶级评论为空 */
    private Long parentId;

    /** @回复的用户 ID（回复场景） */
    private Long replyToUserId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论最多 2000 字")
    private String content;
}
