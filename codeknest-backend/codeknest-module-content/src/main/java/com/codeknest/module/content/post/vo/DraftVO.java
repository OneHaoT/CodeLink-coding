package com.codeknest.module.content.post.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DraftVO {
    /** 草稿 ID（MongoDB ObjectId 字符串，阶段 5 起草稿存储于 MongoDB） */
    private String id;
    private Long postId;
    private String title;
    private String content;
    private String summary;
    private String coverImage;
    private List<String> tagNames;
    private Integer categoryId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
