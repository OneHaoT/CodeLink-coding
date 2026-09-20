package com.codeknest.module.account.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关注关系表 t_user_follow（联合主键 follower_id + following_id）
 */
@Data
@TableName("t_user_follow")
public class UserFollow {

    @TableId(value = "follower_id", type = IdType.INPUT)
    private Long followerId;

    private Long followingId;

    private LocalDateTime createdAt;
}
