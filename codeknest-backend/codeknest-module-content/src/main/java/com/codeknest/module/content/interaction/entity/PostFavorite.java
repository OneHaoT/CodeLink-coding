package com.codeknest.module.content.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章收藏 t_post_favorite（联合主键 user_id + post_id）
 */
@Data
@TableName("t_post_favorite")
public class PostFavorite {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Long postId;

    private LocalDateTime createdAt;
}
