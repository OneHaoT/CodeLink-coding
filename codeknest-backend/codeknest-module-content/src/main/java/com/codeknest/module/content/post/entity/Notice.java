package com.codeknest.module.content.post.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.codeknest.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社区公告表 t_notice
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_notice")
public class Notice extends BaseEntity {

    /** 公告标题 */
    private String title;

    /** 公告内容 */
    private String content;

    /** 1=已发布 0=下架 */
    private Integer status;

    /** 排序权重，越大越靠前 */
    private Integer sortOrder;
}
