package com.codeknest.module.account.auth.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应 VO
 */
@Data
@Builder
public class LoginVO {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn; // 秒

    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private String email;
        private String username;
        private String avatar;
        private String role;
    }
}
