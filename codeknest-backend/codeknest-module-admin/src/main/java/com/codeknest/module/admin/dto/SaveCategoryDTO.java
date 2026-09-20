package com.codeknest.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建/编辑分类
 */
@Data
public class SaveCategoryDTO {

    @NotBlank(message = "分类名不能为空")
    @Size(max = 32, message = "分类名最长 32 字符")
    private String name;

    /** 留空则按名称自动生成 */
    @Size(max = 64, message = "slug 最长 64 字符")
    private String slug;

    @Size(max = 200, message = "描述最长 200 字符")
    private String description;

    private Integer sortOrder;
}
