package com.codeknest.module.content.comment.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论树节点（二级嵌套：顶级 + replies）
 */
@Data
public class CommentVO {

    private Long id;
    private Long postId;
    private Long parentId;

    private Long userId;
    private String username;
    private String userAvatar;

    private Long replyToUserId;
    private String replyToUsername;

    private String content;
    private Integer likeCount;
    private Boolean isLiked;
    private LocalDateTime createdAt;

    private List<CommentVO> replies = new ArrayList<>();
}
