package com.codeknest.module.content.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布 / 更新文章 DTO
 */
@Data
public class SavePostDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200)
    private String title;

    @Size(max = 500)
    private String summary;

    @NotBlank(message = "正文不能为空")
    private String content;

    private Integer categoryId;

    /** 已有标签 ID（优先） */
    private List<Long> tagIds;

    /** 新标签名称（不存在则自动创建） */
    private List<String> tagNames;
}
