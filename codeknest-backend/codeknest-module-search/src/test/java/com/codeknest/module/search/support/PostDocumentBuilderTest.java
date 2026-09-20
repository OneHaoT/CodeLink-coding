package com.codeknest.module.search.support;

import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.content.post.entity.Category;
import com.codeknest.module.content.post.entity.Post;
import com.codeknest.module.content.post.entity.PostTag;
import com.codeknest.module.content.post.entity.Tag;
import com.codeknest.module.content.post.mapper.CategoryMapper;
import com.codeknest.module.content.post.mapper.PostTagMapper;
import com.codeknest.module.content.post.mapper.TagMapper;
import com.codeknest.module.search.document.PostDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MySQL → ES 文档转换器单元测试 — 纯 Mockito
 */
@ExtendWith(MockitoExtension.class)
class PostDocumentBuilderTest {

    private static final Long POST_ID = 77L;

    @Mock
    private UserMapper userMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private PostTagMapper postTagMapper;
    @Mock
    private TagMapper tagMapper;

    private PostDocumentBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new PostDocumentBuilder(userMapper, categoryMapper, postTagMapper, tagMapper);
    }

    private Post fullPost() {
        Post post = new Post();
        post.setId(POST_ID);
        post.setUserId(5L);
        post.setCategoryId(3);
        post.setTitle("Java 并发教程");
        post.setSummary("线程池实战");
        post.setContent("正文内容");
        post.setViewCount(10);
        post.setLikeCount(2);
        post.setPublishedAt(LocalDateTime.of(2026, 9, 19, 12, 0));
        return post;
    }

    private PostTag postTag(Long postId, Long tagId) {
        PostTag rel = new PostTag();
        rel.setPostId(postId);
        rel.setTagId(tagId);
        return rel;
    }

    @Test
    @DisplayName("null 文章 — 返回 null")
    void build_null_returnsNull() {
        assertThat(builder.build(null)).isNull();
    }

    @Test
    @DisplayName("空列表 — 返回空集合且不查询任何表")
    void buildAll_empty_noQuery() {
        assertThat(builder.buildAll(Collections.emptyList())).isEmpty();
        verifyNoInteractions(userMapper, categoryMapper, postTagMapper, tagMapper);
    }

    @Test
    @DisplayName("完整映射 — 作者名/分类名/标签名/Instant 转换全部写入文档")
    void build_mapsAllFields() {
        Post post = fullPost();

        User author = new User();
        author.setId(5L);
        author.setUsername("admin");

        Category category = new Category();
        category.setId(3L);
        category.setName("后端");

        Tag tag = new Tag();
        tag.setId(10L);
        tag.setName("Java");

        when(userMapper.selectBatchIds(any())).thenReturn(List.of(author));
        when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(category));
        when(postTagMapper.selectList(any())).thenReturn(List.of(postTag(POST_ID, 10L)));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag));

        PostDocument doc = builder.build(post);

        assertThat(doc.getId()).isEqualTo(POST_ID);
        assertThat(doc.getUserId()).isEqualTo(5L);
        assertThat(doc.getUsername()).isEqualTo("admin");
        assertThat(doc.getTitle()).isEqualTo("Java 并发教程");
        assertThat(doc.getSummary()).isEqualTo("线程池实战");
        assertThat(doc.getContent()).isEqualTo("正文内容");
        assertThat(doc.getCategoryId()).isEqualTo(3);
        assertThat(doc.getCategoryName()).isEqualTo("后端");
        assertThat(doc.getTags()).containsExactly("Java");
        assertThat(doc.getViewCount()).isEqualTo(10);
        assertThat(doc.getLikeCount()).isEqualTo(2);
        assertThat(doc.getPublishedAt())
                .isEqualTo(post.getPublishedAt().atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("无关联数据 — 作者/分类/标签为空时字段留空不报错")
    void build_missingRelations_toleratesNull() {
        Post post = fullPost();
        post.setCategoryId(null);

        when(userMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());
        when(postTagMapper.selectList(any())).thenReturn(Collections.emptyList());

        PostDocument doc = builder.build(post);

        assertThat(doc.getUsername()).isNull();
        assertThat(doc.getCategoryName()).isNull();
        assertThat(doc.getTags()).isEmpty();
    }
}
