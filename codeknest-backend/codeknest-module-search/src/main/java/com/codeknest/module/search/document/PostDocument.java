package com.codeknest.module.search.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

import java.time.Instant;
import java.util.List;

/**
 * 文章检索文档 — 对应 ES 索引 codeknest_post
 * <p>
 * 索引仅保存「已发布且未逻辑删除」的文章，因此无需额外状态字段做过滤。
 */
@Data
@Document(indexName = PostDocument.INDEX_NAME, createIndex = false)
public class PostDocument {

    public static final String INDEX_NAME = "codeknest_post";

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Keyword)
    private String username;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String summary;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    @Field(type = FieldType.Integer)
    private Integer categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    /**
     * 文章标签 — 多字段：
     * <ul>
     *   <li>主字段 Keyword：原样保留，用于展示与精确匹配（term 过滤）；</li>
     *   <li>子字段 {@code tags.text}：IK 分词（英文自动小写，如 Java→java），参与全文检索。</li>
     * </ul>
     */
    @MultiField(mainField = @Field(type = FieldType.Keyword),
            otherFields = @InnerField(suffix = "text", type = FieldType.Text,
                    analyzer = "ik_max_word", searchAnalyzer = "ik_smart"))
    private List<String> tags;

    @Field(type = FieldType.Integer)
    private Integer viewCount;

    @Field(type = FieldType.Integer)
    private Integer likeCount;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant publishedAt;
}
