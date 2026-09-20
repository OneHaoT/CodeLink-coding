package com.codeknest.module.content.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论点赞 t_comment_like（联合主键）
 */
@Data
@TableName("t_comment_like")
public class CommentLike {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Long commentId;

    private LocalDateTime createdAt;
}
