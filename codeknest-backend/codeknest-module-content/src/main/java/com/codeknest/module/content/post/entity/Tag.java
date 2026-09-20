package com.codeknest.module.content.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 标签表 t_tag
 */
@Data
@TableName("t_tag")
public class Tag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String slug;
    private Integer postCount;
    private LocalDateTime createdAt;
}
