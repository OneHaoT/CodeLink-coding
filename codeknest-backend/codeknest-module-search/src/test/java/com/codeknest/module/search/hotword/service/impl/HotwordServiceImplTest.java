package com.codeknest.module.search.hotword.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeknest.module.search.hotword.entity.SearchHotword;
import com.codeknest.module.search.hotword.mapper.SearchHotwordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 搜索热词单元测试 — 纯 Mockito，不启动 Spring、不连 Redis / MySQL
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class HotwordServiceImplTest {

    private static final String PENDING_KEY = "search:hotword:pending";
    private static final String TOP_KEY = "search:hotword:top:10";

    @Mock
    private SearchHotwordMapper hotwordMapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ZSetOperations<String, String> zSetOps;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HotwordServiceImpl hotwordService;

    @BeforeEach
    void setUp() {
        hotwordService = new HotwordServiceImpl(hotwordMapper, redis, objectMapper);
    }

    private ZSetOperations.TypedTuple<String> tuple(String keyword, double score) {
        return new DefaultTypedTuple<>(keyword, score);
    }

    // ==================== record ====================

    @Test
    @DisplayName("记录搜索 — 只累加 Redis 增量（不打 DB）并刷新增量过期时间")
    void record_onlyAccumulatesInRedis() {
        when(redis.opsForZSet()).thenReturn(zSetOps);

        hotwordService.record("  java  ");

        verify(zSetOps).incrementScore(PENDING_KEY, "java", 1);
        verify(redis).expire(PENDING_KEY, Duration.ofDays(7));
        verifyNoInteractions(hotwordMapper);
    }

    @Test
    @DisplayName("记录搜索 — 空白或超长关键词直接忽略")
    void record_blankOrTooLong_ignored() {
        hotwordService.record(null);
        hotwordService.record("   ");
        hotwordService.record("x".repeat(65));

        verifyNoInteractions(redis, hotwordMapper);
    }

    @Test
    @DisplayName("记录搜索 — Redis 不可用时降级为直接落库（MySQL 权威不丢）")
    void record_redisDown_fallsBackToDb() {
        when(redis.opsForZSet()).thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> hotwordService.record("java")).doesNotThrowAnyException();

        ArgumentCaptor<List<SearchHotword>> captor = ArgumentCaptor.forClass(List.class);
        verify(hotwordMapper).upsertBatch(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getKeyword()).isEqualTo("java");
        assertThat(captor.getValue().get(0).getSearchCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("记录搜索 — Redis 与 DB 同时不可用也不影响搜索主流程")
    void record_redisAndDbDown_swallows() {
        when(redis.opsForZSet()).thenThrow(new RuntimeException("redis down"));
        when(hotwordMapper.upsertBatch(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> hotwordService.record("java")).doesNotThrowAnyException();
    }

    // ==================== flushToDb ====================

    @Test
    @DisplayName("落库 — 无增量时不做任何 DB 操作")
    void flush_noPending_doesNothing() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.rangeWithScores(PENDING_KEY, 0L, 499L)).thenReturn(Set.of());

        assertThat(hotwordService.flushToDb()).isZero();

        verifyNoInteractions(hotwordMapper);
    }

    @Test
    @DisplayName("落库 — 批量 upsert 后按增量回扣并清理 TopN 读缓存")
    void flush_upsertsThenDeducts() {
        LocalDateTime before = LocalDateTime.now();
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.rangeWithScores(PENDING_KEY, 0L, 499L))
                .thenReturn(Set.of(tuple("java", 3.0), tuple("redis", 1.0)));
        when(hotwordMapper.upsertBatch(any())).thenReturn(2);

        assertThat(hotwordService.flushToDb()).isEqualTo(2);

        ArgumentCaptor<List<SearchHotword>> captor = ArgumentCaptor.forClass(List.class);
        verify(hotwordMapper).upsertBatch(captor.capture());
        assertThat(captor.getValue()).extracting(SearchHotword::getKeyword)
                .containsExactlyInAnyOrder("java", "redis");
        assertThat(captor.getValue().get(0).getLastSearchedAt()).isAfterOrEqualTo(before);

        // 按增量回扣：并发新增的计数仍留在增量中，不会被误清
        verify(zSetOps).incrementScore(PENDING_KEY, "java", -3.0);
        verify(zSetOps).incrementScore(PENDING_KEY, "redis", -1.0);
        verify(zSetOps).removeRangeByScore(PENDING_KEY, Double.NEGATIVE_INFINITY, 0);
        verify(redis).scan(any());
    }

    @Test
    @DisplayName("落库失败 — 保留增量待下轮重试，不回扣")
    void flush_dbFailure_keepsPending() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.rangeWithScores(PENDING_KEY, 0L, 499L)).thenReturn(Set.of(tuple("java", 3.0)));
        when(hotwordMapper.upsertBatch(any())).thenThrow(new RuntimeException("db down"));

        assertThat(hotwordService.flushToDb()).isZero();

        verify(zSetOps, never()).incrementScore(eq(PENDING_KEY), eq("java"), anyDouble());
    }

    @Test
    @DisplayName("落库 — 增量读取异常时跳过本轮，不抛错")
    void flush_redisReadFailure_skips() {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.rangeWithScores(PENDING_KEY, 0L, 499L)).thenThrow(new RuntimeException("redis down"));

        assertThat(hotwordService.flushToDb()).isZero();

        verifyNoInteractions(hotwordMapper);
    }

    // ==================== topN ====================

    @Test
    @DisplayName("TopN — 读缓存命中时不回源 MySQL")
    void topN_cacheHit_skipsDb() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(TOP_KEY)).thenReturn(objectMapper.writeValueAsString(List.of("java", "redis")));

        assertThat(hotwordService.topN(10)).containsExactly("java", "redis");

        verifyNoInteractions(hotwordMapper);
    }

    @Test
    @DisplayName("TopN — 未命中回源 MySQL 并按次数倒序返回，随后回填缓存")
    void topN_cacheMiss_readsDbAndBackfills() {
        Page<SearchHotword> page = new Page<>(1, 10);
        page.setRecords(List.of(
                new SearchHotword("java", 9L, LocalDateTime.now()),
                new SearchHotword("redis", 3L, LocalDateTime.now())));

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(TOP_KEY)).thenReturn(null);
        when(hotwordMapper.selectPage(any(), any())).thenReturn(page);

        assertThat(hotwordService.topN(10)).containsExactly("java", "redis");

        verify(valueOps).set(eq(TOP_KEY), any(String.class), any(Duration.class));
    }

    @Test
    @DisplayName("TopN — 缓存与 DB 均异常时返回空列表，不抛错")
    void topN_allDown_returnsEmpty() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(TOP_KEY)).thenReturn(null);
        when(hotwordMapper.selectPage(any(), any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> assertThat(hotwordService.topN(10)).isEmpty()).doesNotThrowAnyException();
    }
}