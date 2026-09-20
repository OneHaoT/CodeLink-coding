package com.codeknest.module.content.post.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codeknest.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文章主表 t_post
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_post")
public class Post extends BaseEntity {

    private Long userId;
    private Integer categoryId;
    private String title;
    private String summary;
    private String content;
    private String coverImage;

    /** 0=已删除 1=已发布 2=待审核 3=审核拒绝 */
    private Integer status;

    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;

    private LocalDateTime publishedAt;
}
