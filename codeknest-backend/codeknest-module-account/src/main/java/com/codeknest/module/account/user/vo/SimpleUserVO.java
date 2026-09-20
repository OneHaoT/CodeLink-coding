package com.codeknest.module.account.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 粉丝/关注列表项
 */
@Data
@AllArgsConstructor
public class SimpleUserVO {
    private Long id;
    private String username;
    private String avatar;
    private String bio;
}
