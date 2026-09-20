package com.codeknest.common.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户上下文（ThreadLocal）
 */
public class SecurityContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long getUserId() {
        CurrentUser u = HOLDER.get();
        return u != null ? u.getUserId() : null;
    }

    public static String getUsername() {
        CurrentUser u = HOLDER.get();
        return u != null ? u.getUsername() : null;
    }

    public static String getRole() {
        CurrentUser u = HOLDER.get();
        return u != null ? u.getRole() : null;
    }

    /**
     * 只有登录用户才能调用，未登录抛异常
     */
    public static Long requireUserId() {
        Long id = getUserId();
        if (id == null) {
            throw new com.codeknest.common.core.BusinessException(com.codeknest.common.core.ErrorCode.UNAUTHORIZED);
        }
        return id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentUser {
        private Long userId;
        private String username;
        private String role;
    }
}
