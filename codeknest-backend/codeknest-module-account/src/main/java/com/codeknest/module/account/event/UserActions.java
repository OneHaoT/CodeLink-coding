package com.codeknest.module.account.event;

import java.util.Set;

/**
 * 用户动作常量 — user_action_log 的 action 取值；其中 feed 类动作同时写入 user_activity。
 * <p>
 * 全部动作的事实来源都在 MySQL（t_user / t_post / t_comment / t_post_like / t_post_favorite / t_user_follow），
 * MongoDB 侧仅为派生投影。
 */
public final class UserActions {

    private UserActions() {}

    // ---- 账号 / 安全 ----
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    public static final String LOGOUT = "LOGOUT";
    public static final String REGISTER = "REGISTER";

    // ---- 内容 ----
    public static final String POST_PUBLISH = "POST_PUBLISH";
    public static final String POST_DELETE = "POST_DELETE";
    public static final String COMMENT_CREATE = "COMMENT_CREATE";
    public static final String COMMENT_DELETE = "COMMENT_DELETE";

    // ---- 社交 / 互动 ----
    public static final String FOLLOW = "FOLLOW";
    public static final String UNFOLLOW = "UNFOLLOW";
    public static final String POST_LIKE = "POST_LIKE";
    public static final String POST_FAVORITE = "POST_FAVORITE";

    /** 目标类型 */
    public static final String TARGET_POST = "POST";
    public static final String TARGET_COMMENT = "COMMENT";
    public static final String TARGET_USER = "USER";

    /** 会展示在用户动态流里的动作 */
    private static final Set<String> FEED_ACTIONS = Set.of(
            POST_PUBLISH, COMMENT_CREATE, FOLLOW, POST_LIKE, POST_FAVORITE);

    public static boolean isFeedAction(String action) {
        return action != null && FEED_ACTIONS.contains(action);
    }
}