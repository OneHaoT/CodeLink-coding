package com.codeknest.module.account.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知消息表 t_notification
 */
@Data
@TableName("t_notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** LIKE_POST / LIKE_COMMENT / COMMENT_POST / REPLY_COMMENT / NEW_FOLLOWER / SYSTEM */
    private String type;

    private String title;
    private String content;
    private Long sourceUserId;
    private Long sourcePostId;
    private Long sourceCommentId;

    @TableField("is_read")
    private Integer isRead;

    private LocalDateTime readAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
