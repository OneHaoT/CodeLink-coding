package com.codeknest.module.content.post.dto;

import lombok.Data;

import java.util.List;

/**
 * 保存草稿 DTO
 */
@Data
public class SaveDraftDTO {
    /** 草稿 ID（MongoDB ObjectId 字符串；为空表示新建） */
    private String id;
    private Long postId;
    private String title;
    private String content;
    private String summary;
    private List<String> tagNames;
    private Integer categoryId;
}
