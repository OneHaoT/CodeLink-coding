package com.codeknest.module.account.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户详情表 — 与 t_user 1:1
 */
@Data
@TableName("t_user_profile")
public class UserProfile {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private String bio;
    private String website;
    private String location;
    private String company;
    private String github;

    @TableField("followers_count")
    private Integer followersCount;

    @TableField("following_count")
    private Integer followingCount;

    @TableField("posts_count")
    private Integer postsCount;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
