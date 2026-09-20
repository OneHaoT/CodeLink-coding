package com.codeknest.module.content.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章点赞 t_post_like（联合主键 user_id + post_id）
 */
@Data
@TableName("t_post_like")
public class PostLike {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Long postId;

    private LocalDateTime createdAt;
}
