package com.codeknest.module.account.user.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 用户主页 / 当前用户资料 VO
 */
@Data
@Builder
public class UserHomeVO {

    private Long id;
    private String username;
    private String avatar;
    private String role;
    private String bio;
    private String website;
    private String location;
    private String company;
    private String github;
    private Integer followersCount;
    private Integer followingCount;
    private Integer postsCount;
    /** 当前登录用户是否已关注 TA（未登录为 false） */
    private Boolean following;
}
