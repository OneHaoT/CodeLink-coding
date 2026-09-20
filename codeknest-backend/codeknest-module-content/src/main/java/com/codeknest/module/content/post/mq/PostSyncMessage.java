package com.codeknest.module.content.post.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文章 ES 同步消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostSyncMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 需要同步的文章 ID */
    private Long postId;
}
