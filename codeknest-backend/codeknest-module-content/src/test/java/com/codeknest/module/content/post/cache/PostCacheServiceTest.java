package com.codeknest.module.content.post.cache;

import com.codeknest.module.content.post.vo.PostVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文章详情缓存单元测试 — 覆盖缓存穿透 / 击穿 / 雪崩三防护（纯 Mockito，不连 Redis）
 */
@ExtendWith(MockitoExtension.class)
class PostCacheServiceTest {

    private static final Long POST_ID = 77L;
    private static final String DETAIL_KEY = "post:detail:77";
    private static final String LOCK_KEY = "post:detail:lock:77";
    private static final String LOCK_TTL = "1";
    private static final Duration LOCK_DURATION = Duration.ofSeconds(10);

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private PostCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new PostCacheService(redis, objectMapper);
    }

    private PostVO post(String title) {
        return PostVO.builder().id(POST_ID).title(title).build();
    }

    private String json(PostVO vo) throws Exception {
        return objectMapper.writeValueAsString(vo);
    }

    // ==================== 命中 ====================

    @Test
    @DisplayName("详情缓存命中 — 直接返回反序列化结果，不回源")
    void loadDetail_cacheHit_skipsLoader() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(DETAIL_KEY)).thenReturn(json(post("缓存文章")));

        PostVO vo = cacheService.loadDetail(POST_ID, () -> {
            throw new AssertionError("命中缓存不应回源");
        });

        assertThat(vo.getTitle()).isEqualTo("缓存文章");
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("穿透防护 — 空值哨兵命中即判定不存在，不回源")
    void loadDetail_nullSentinel_skipsLoader() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(DETAIL_KEY)).thenReturn("__NULL__");

        PostVO vo = cacheService.loadDetail(POST_ID, () -> {
            throw new AssertionError("空值哨兵命中不应回源");
        });

        assertThat(vo).isNull();
    }

    @Test
    @DisplayName("缓存内容损坏 — 视为未命中并回源")
    void loadDetail_brokenJson_treatedAsMiss() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(DETAIL_KEY)).thenReturn("{not-json");
        when(valueOps.setIfAbsent(LOCK_KEY, LOCK_TTL, LOCK_DURATION)).thenReturn(true);

        PostVO vo = cacheService.loadDetail(POST_ID, () -> post("回源文章"));

        assertThat(vo.getTitle()).isEqualTo("回源文章");
    }

    // ==================== 击穿防护：单飞重建 ====================

    @Test
    @DisplayName("击穿防护 — 未命中时抢锁重建，只回源一次并写回缓存")
    void loadDetail_miss_rebuildsOnceUnderLock() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(DETAIL_KEY)).thenReturn(null);
        when(valueOps.setIfAbsent(LOCK_KEY, LOCK_TTL, LOCK_DURATION)).thenReturn(true);

        PostVO loaded = post("回源文章");
        PostVO vo = cacheService.loadDetail(POST_ID, () -> loaded);

        assertThat(vo).isSameAs(loaded);
        verify(valueOps).set(eq(DETAIL_KEY), any(String.class), any(Duration.class));
        verify(redis).delete(LOCK_KEY);
    }

    @Test
    @DisplayName("击穿防护 — 抢不到锁则短暂等待，等到缓存即返回（不回源）")
    void loadDetail_lockBusy_waitsForCache() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(DETAIL_KEY)).thenReturn(null, json(post("他人重建的缓存")));
        when(valueOps.setIfAbsent(LOCK_KEY, LOCK_TTL, LOCK_DURATION)).thenReturn(false);

        PostVO vo = cacheService.loadDetail(POST_ID, () -> {
            throw new AssertionError("等待到缓存后不应回源");
        });

        assertThat(vo.getTitle()).isEqualTo("他人重建的缓存");
    }

    @Test
    @DisplayName("击穿防护 — 等待超时则降级直查 DB 且不写缓存（避免并发覆盖）")
    void loadDetail_lockBusyTimeout_fallsBackToLoader() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(DETAIL_KEY)).thenReturn(null);
        when(valueOps.setIfAbsent(LOCK_KEY, LOCK_TTL, LOCK_DURATION)).thenReturn(false);

        PostVO loaded = post("降级文章");
        PostVO vo = cacheService.loadDetail(POST_ID, () -> loaded);

        assertThat(vo).isSameAs(loaded);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    // ==================== 降级 ====================

    @Test
    @DisplayName("Redis 不可用 — 视为未命中直接回源，接口不抛错")
    void loadDetail_redisDown_fallsBackWithoutThrowing() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(DETAIL_KEY)).thenThrow(new RuntimeException("redis down"));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        PostVO loaded = post("降级文章");

        assertThatCode(() -> assertThat(cacheService.loadDetail(POST_ID, () -> loaded)).isSameAs(loaded))
                .doesNotThrowAnyException();
    }

    // ==================== 穿透写哨兵 + 雪崩抖动 ====================

    @Test
    @DisplayName("穿透防护 — 回源结果为空时写入短 TTL 空值哨兵")
    void putDetail_null_writesSentinel() {
        when(redis.opsForValue()).thenReturn(valueOps);

        cacheService.putDetail(POST_ID, null);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq(DETAIL_KEY), eq("__NULL__"), ttl.capture());
        assertThat(ttl.getValue()).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(66));
    }

    @Test
    @DisplayName("雪崩防护 — 同一键多次写入的 TTL 落在抖动区间内")
    void putDetail_ttlJittered() {
        when(redis.opsForValue()).thenReturn(valueOps);

        cacheService.putDetail(POST_ID, post("文章"));
        cacheService.putDetail(POST_ID, null);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps, times(2)).set(eq(DETAIL_KEY), anyString(), ttl.capture());
        List<Duration> ttls = ttl.getAllValues();
        assertThat(ttls.get(0)).isBetween(Duration.ofMinutes(30), Duration.ofMinutes(33));
        assertThat(ttls.get(1)).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(66));
    }
}