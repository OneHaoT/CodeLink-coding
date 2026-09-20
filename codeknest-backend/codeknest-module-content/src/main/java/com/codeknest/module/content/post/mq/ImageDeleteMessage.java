package com.codeknest.module.content.post.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 图片删除消息体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageDeleteMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待删除的图片 URL 列表（仅临时图片 URL 会被处理） */
    private List<String> urls;
}
