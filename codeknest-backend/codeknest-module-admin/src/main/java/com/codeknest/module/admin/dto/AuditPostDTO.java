package com.codeknest.module.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文章审核请求
 */
@Data
public class AuditPostDTO {

    /** 1=通过 3=拒绝 */
    @NotNull(message = "审核状态不能为空")
    private Integer status;

    /** 拒绝原因（拒绝时建议填写） */
    private String reason;
}
