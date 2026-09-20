package com.codeknest.module.content.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 文章-标签关联 t_post_tag（联合主键）
 */
@Data
@TableName("t_post_tag")
public class PostTag {

    @TableId(value = "post_id", type = IdType.INPUT)
    private Long postId;

    private Long tagId;
}
