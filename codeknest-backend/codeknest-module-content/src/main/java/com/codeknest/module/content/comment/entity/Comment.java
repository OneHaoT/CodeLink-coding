package com.codeknest.module.content.comment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codeknest.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论表 t_comment（二级嵌套：parent_id 为空=顶级）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_comment")
public class Comment extends BaseEntity {

    private Long postId;
    private Long userId;
    private Long parentId;
    private Long replyToUserId;
    private String content;
    private Integer likeCount;

    /** 0=已删除 1=正常 2=待审核 */
    private Integer status;
}
