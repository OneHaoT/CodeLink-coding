package com.codeknest.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建/编辑标签
 */
@Data
public class SaveTagDTO {

    @NotBlank(message = "标签名不能为空")
    @Size(max = 32, message = "标签名最长 32 字符")
    private String name;

    /** 留空则按名称自动生成 */
    @Size(max = 64, message = "slug 最长 64 字符")
    private String slug;
}
