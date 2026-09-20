package com.codeknest.module.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端文章列表项
 */
@Data
public class AdminPostVO {

    private Long id;
    private String title;

    private SimpleAuthor author;
    private SimpleCategory category;

    /** 0=已删除 1=已发布 2=待审核 3=审核拒绝 */
    private Integer status;

    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;

    private LocalDateTime createdAt;

    @Data
    public static class SimpleAuthor {
        private Long id;
        private String username;
    }

    @Data
    public static class SimpleCategory {
        private Integer id;
        private String name;
    }
}
