package com.codeknest.module.content.post.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章列表项 / 详情统一 VO（详情时 content 等字段才有值）
 * 缓存场景需 Jackson 反序列化，必须保留无参构造器
 */
@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class PostVO {

    private Long id;
    private String title;
    private String summary;
    private String content;
    private String coverImage;

    private Author author;
    private CategoryInfo category;
    private List<TagInfo> tags;

    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;

    private Boolean isLiked;
    private Boolean isFavorited;
    private Boolean isFollowingAuthor;

    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;

    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Author {
        private Long id;
        private String username;
        private String avatar;
    }

    @Data
    @Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CategoryInfo {
        private Integer id;
        private String name;
    }

    @Data
    @Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class TagInfo {
        private Long id;
        private String name;
    }
}
