package com.codeknest.module.search.service.impl;

import com.codeknest.module.content.post.entity.Post;
import com.codeknest.module.content.post.mapper.PostMapper;
import com.codeknest.module.search.document.PostDocument;
import com.codeknest.module.search.support.PostDocumentBuilder;
import com.codeknest.module.search.vo.SearchVO;
import com.codeknest.common.mybatis.PageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 检索服务单元测试 — 纯 Mockito，不启动 Spring、不连 ES
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class SearchServiceImplTest {

    private static final Long POST_ID = 77L;

    @Mock
    private ElasticsearchOperations operations;
    @Mock
    private PostMapper postMapper;
    @Mock
    private PostDocumentBuilder documentBuilder;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ZSetOperations<String, String> zSetOps;

    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchServiceImpl(operations, postMapper, documentBuilder, redis);
    }

    private Post publishedPost() {
        Post post = new Post();
        post.setId(POST_ID);
        post.setUserId(5L);
        post.setTitle("Java 并发教程");
        post.setSummary("线程池实战");
        post.setContent("正文内容");
        post.setCategoryId(3);
        post.setStatus(1);
        post.setViewCount(10);
        post.setPublishedAt(LocalDateTime.of(2026, 9, 19, 12, 0));
        return post;
    }

    private PostDocument docOf(Post post) {
        PostDocument doc = new PostDocument();
        doc.setId(post.getId());
        doc.setUserId(post.getUserId());
        doc.setUsername("admin");
        doc.setTitle(post.getTitle());
        doc.setSummary(post.getSummary());
        doc.setTags(List.of("Java"));
        doc.setViewCount(post.getViewCount());
        doc.setPublishedAt(post.getPublishedAt().atZone(ZoneId.systemDefault()).toInstant());
        return doc;
    }

    // ==================== search ====================

    @Test
    @DisplayName("空关键词 — 直接返回空页，不查询 ES")
    void search_blankQ_returnsEmptyWithoutEs() {
        PageVO<SearchVO> vo = searchService.search("   ", null, null, null);

        assertThat(vo.getItems()).isEmpty();
        assertThat(vo.getTotal()).isZero();
        assertThat(vo.getTotalPages()).isZero();
        assertThat(vo.getPage()).isEqualTo(1);
        assertThat(vo.getSize()).isEqualTo(10);
        verifyNoInteractions(operations, redis);
    }

    @Test
    @DisplayName("关键词检索 — 命中高亮片段优先，回退原文")
    void search_withHits_mapsHighlightAndFallback() {
        PostDocument doc = new PostDocument();
        doc.setId(POST_ID);
        doc.setUserId(5L);
        doc.setUsername("admin");
        doc.setTitle("Java 并发教程");
        doc.setSummary("线程池实战");
        doc.setTags(List.of("Java"));
        doc.setViewCount(10);
        doc.setPublishedAt(Instant.parse("2026-09-19T04:00:00Z"));

        SearchHit<PostDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);
        when(hit.getHighlightFields()).thenReturn(Map.of("title", List.of("<em>Java</em> 并发教程")));

        SearchHits<PostDocument> hits = mock(SearchHits.class);
        when(hits.getTotalHits()).thenReturn(1L);
        when(hits.getSearchHits()).thenReturn(List.of(hit));
        when(operations.search(any(Query.class), eq(PostDocument.class))).thenReturn(hits);
        when(redis.opsForZSet()).thenReturn(zSetOps);

        PageVO<SearchVO> vo = searchService.search("java", 1, 10, "relevance");

        assertThat(vo.getTotal()).isEqualTo(1);
        assertThat(vo.getTotalPages()).isEqualTo(1);
        assertThat(vo.getItems()).hasSize(1);
        SearchVO item = vo.getItems().get(0);
        assertThat(item.getId()).isEqualTo(POST_ID);
        assertThat(item.getTitle()).isEqualTo("<em>Java</em> 并发教程");
        assertThat(item.getSummary()).isEqualTo("线程池实战");
        assertThat(item.getAuthor().getId()).isEqualTo(5L);
        assertThat(item.getAuthor().getUsername()).isEqualTo("admin");
        assertThat(item.getTags()).containsExactly("Java");
        assertThat(item.getPublishedAt()).isNotNull();
        // 搜索成功后关键词记入热词（ZINCRBY + 滚动 1 天过期）
        verify(zSetOps).incrementScore("search:hotwords", "java", 1);
        verify(redis).expire(eq("search:hotwords"), eq(Duration.ofDays(1)));
    }

    @Test
    @DisplayName("热词统计 — Redis 异常被吞掉，不影响搜索主流程")
    void search_redisFailure_swallows() {
        SearchHits<PostDocument> hits = mock(SearchHits.class);
        when(hits.getTotalHits()).thenReturn(0L);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(Query.class), eq(PostDocument.class))).thenReturn(hits);
        when(redis.opsForZSet()).thenThrow(new RuntimeException("redis down"));

        PageVO<SearchVO> vo = searchService.search("java", 1, 10, null);

        assertThat(vo.getTotal()).isZero();
        assertThat(vo.getItems()).isEmpty();
    }

    @Test
    @DisplayName("热词查询 — 按 ZSET 分数倒序返回 TopN")
    void hotwords_returnsTopN() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.reverseRange("search:hotwords", 0, 9))
                .thenReturn(new LinkedHashSet<>(List.of("java", "redis")));

        List<String> words = searchService.hotwords(10);

        assertThat(words).containsExactly("java", "redis");
    }

    // ==================== syncPost（幂等 upsert / 删除） ====================

    @Test
    @DisplayName("同步 — postId 为空直接忽略")
    void syncPost_nullId_ignored() {
        searchService.syncPost(null);
        verifyNoInteractions(operations, postMapper);
    }

    @Test
    @DisplayName("同步 — 文章不存在或未发布时删除索引文档（幂等删除）")
    void syncPost_notPublished_deletesDocument() {
        when(postMapper.selectById(POST_ID)).thenReturn(null);

        searchService.syncPost(POST_ID);

        verify(operations).delete("77", PostDocument.class);
        verify(operations, never()).save(any(PostDocument.class));
    }

    @Test
    @DisplayName("同步 — 已发布文章写入文档并刷新索引（幂等 upsert）")
    void syncPost_published_upsertsDocument() {
        Post post = publishedPost();
        PostDocument doc = docOf(post);
        IndexOperations indexOps = mock(IndexOperations.class);

        when(postMapper.selectById(POST_ID)).thenReturn(post);
        when(documentBuilder.build(post)).thenReturn(doc);
        when(operations.save(any(PostDocument.class))).thenReturn(doc);
        when(operations.indexOps(PostDocument.class)).thenReturn(indexOps);

        searchService.syncPost(POST_ID);

        verify(operations).save(doc);
        verify(indexOps).refresh();
        verify(operations, never()).delete(any(String.class), eq(PostDocument.class));
    }

    @Test
    @DisplayName("同步 — ES 异常不向上抛出（避免 MQ 无限重投）")
    void syncPost_esFailure_swallows() {
        Post post = publishedPost();
        PostDocument doc = docOf(post);

        when(postMapper.selectById(POST_ID)).thenReturn(post);
        when(documentBuilder.build(post)).thenReturn(doc);
        when(operations.save(any(PostDocument.class))).thenThrow(new RuntimeException("es down"));

        assertThatCode(() -> searchService.syncPost(POST_ID)).doesNotThrowAnyException();
    }

    // ==================== reindex ====================

    @Test
    @DisplayName("全量重建 — 删旧索引建新索引并批量写入")
    void reindex_rebuildsIndexAndSaves() {
        Post post = publishedPost();
        PostDocument doc = docOf(post);
        IndexOperations indexOps = mock(IndexOperations.class);

        when(operations.indexOps(PostDocument.class)).thenReturn(indexOps);
        when(indexOps.exists()).thenReturn(true);
        when(postMapper.selectList(any())).thenReturn(List.of(post));
        when(documentBuilder.buildAll(List.of(post))).thenReturn(List.of(doc));

        long count = searchService.reindex();

        assertThat(count).isEqualTo(1);
        verify(indexOps).delete();
        verify(indexOps).createWithMapping();
        verify(operations).save(List.of(doc));
    }
}
