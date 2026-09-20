package com.codeknest.module.content.post.dto;

import lombok.Data;

/**
 * 文章列表查询参数
 */
@Data
public class PostQueryDTO {

    private Long page = 1L;
    private Long size = 10L;

    /** 关键词（标题/摘要模糊） */
    private String q;

    /** 标签名 */
    private String tag;

    private Integer categoryId;

    /** 作者用户 ID */
    private Long userId;

    /** latest=最新 hot=热门 recommend=默认（最新） */
    private String sort;
}
