package com.codeknest.module.content.post.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词 t_sensitive_word
 * <p>
 * 定义在 post 模块，供前台（server-web）与后台（server-admin）共用；
 * 管理端 CRUD 位于 module-admin，内容校验位于 {@link com.codeknest.module.content.post.support.SensitiveWordChecker}。
 */
@Data
@TableName("t_sensitive_word")
public class SensitiveWord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String word;

    /** sensitive/ad/political */
    private String category;

    private LocalDateTime createdAt;
}
