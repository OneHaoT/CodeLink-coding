package com.codeknest.module.content.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分类表 t_category
 */
@Data
@TableName("t_category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String slug;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
