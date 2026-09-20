package com.codeknest.module.search.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 搜索结果项 VO — title/summary 可能含 ES 高亮标签 &lt;em&gt;
 */
@Data
@Builder
public class SearchVO {

    private Long id;
    private String title;
    private String summary;
    private Author author;
    private List<String> tags;
    private Integer viewCount;
    private LocalDateTime publishedAt;

    @Data
    @Builder
    public static class Author {
        private Long id;
        private String username;
    }
}
