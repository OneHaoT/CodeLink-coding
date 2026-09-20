package com.codeknest.module.account.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LoginLogVO {
    private String id;
    private Long userId;
    private String account;
    private Boolean success;
    private String failReason;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
}
