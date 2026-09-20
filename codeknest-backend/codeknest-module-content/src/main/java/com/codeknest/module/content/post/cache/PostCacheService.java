package com.codeknest.module.content.post.cache;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.content.post.vo.PostVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 文章热点数据缓存 — 详情 / 热榜（仅缓存"公共 PostVO"，个性化字段由调用方按登录态补齐）
 * <p>
 * 已覆盖缓存三防护：
 * <ul>
 *   <li><b>穿透</b>：DB 确认不存在时写入空值哨兵（独立短 TTL）；</li>
 *   <li><b>击穿</b>：详情重建走 Redis 互斥锁单飞，未抢到锁的线程短暂等待后降级直查；</li>
 *   <li><b>雪崩</b>：所有 TTL 附加 0~10% 随机抖动。</li>
 * </ul>
 * 序列化失败 / Redis 不可用一律降级直查 DB，不影响业务主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostCacheService {

    private static final String DETAIL_KEY = "post:detail:%d";
    private static final String DETAIL_LOCK_KEY = "post:detail:lock:%d";
    private static final String HOT_KEY_PREFIX = "post:hot:";
    private static final String HOT_KEY = HOT_KEY_PREFIX + "%d:%d";

    /** 空值哨兵：DB 确认文章不存在时写入，防止同一 id 反复穿透到 DB */
    private static final String NULL_SENTINEL = "__NULL__";

    private static final Duration DETAIL_TTL = Duration.ofMinutes(30);
    private static final Duration EMPTY_TTL = Duration.ofSeconds(60);
    private static final Duration HOT_TTL = Duration.ofMinutes(5);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    /** 未抢到重建锁时的等待次数与间隔（最多 200ms） */
    private static final int WAIT_RETRY = 4;
    private static final long WAIT_INTERVAL_MILLIS = 50L;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * 缓存读取结果
     *
     * @param miss  是否未缓存（true=需回源 DB）
     * @param value 命中时的值；{@code null} 表示已确认数据不存在（空值哨兵命中）
     */
    public record Lookup<T>(boolean miss, T value) {

        /** 未缓存 */
        public static <T> Lookup<T> missing() {
            return new Lookup<>(true, null);
        }

        /** 命中（value 为 null 表示已确认数据不存在） */
        public static <T> Lookup<T> hit(T value) {
            return new Lookup<>(false, value);
        }
    }

    /**
     * 带三防护的详情读取：命中直接返回；未命中时单飞重建（其余线程短等待后降级直查）。
     * loader 返回 {@code null} 表示数据不存在，会被写成空值哨兵防止穿透。
     */
    public PostVO loadDetail(Long id, Supplier<PostVO> loader) {
        Lookup<PostVO> cached = getDetail(id);
        if (!cached.miss()) return cached.value();

        if (!tryLockDetail(id)) {
            Lookup<PostVO> rebuilt = awaitDetail(id);
            return rebuilt.miss() ? loader.get() : rebuilt.value();
        }
        try {
            // 双重检查：等待锁期间可能已被其他线程重建
            Lookup<PostVO> recheck = getDetail(id);
            if (!recheck.miss()) return recheck.value();
            PostVO loaded = loader.get();
            putDetail(id, loaded);
            return loaded;
        } finally {
            unlockDetail(id);
        }
    }

    public Lookup<PostVO> getDetail(Long id) {
        String json;
        try {
            json = redis.opsForValue().get(String.format(DETAIL_KEY, id));
        } catch (Exception e) {
            log.warn("post detail cache read failed, id={}", id, e);
            return Lookup.missing();
        }
        if (json == null) return Lookup.missing();
        if (NULL_SENTINEL.equals(json)) return Lookup.hit(null);
        try {
            return Lookup.hit(objectMapper.readValue(json, PostVO.class));
        } catch (Exception e) {
            log.warn("post detail cache deserialize failed, id={}", id, e);
            return Lookup.missing();
        }
    }

    /** vo 为 null 时写入空值哨兵（短 TTL + 抖动） */
    public void putDetail(Long id, PostVO vo) {
        try {
            if (vo == null) {
                redis.opsForValue().set(String.format(DETAIL_KEY, id), NULL_SENTINEL, jitter(EMPTY_TTL));
            } else {
                redis.opsForValue().set(String.format(DETAIL_KEY, id),
                        objectMapper.writeValueAsString(vo), jitter(DETAIL_TTL));
            }
        } catch (Exception e) {
            log.warn("post detail cache write failed, id={}", id, e);
        }
    }

    public PageVO<PostVO> getHot(long page, long size) {
        String json;
        try {
            json = redis.opsForValue().get(String.format(HOT_KEY, page, size));
        } catch (Exception e) {
            log.warn("post hot cache read failed, page={}, size={}", page, size, e);
            return null;
        }
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<PageVO<PostVO>>() {});
        } catch (Exception e) {
            log.warn("post hot cache deserialize failed, page={}, size={}", page, size, e);
            return null;
        }
    }

    public void putHot(long page, long size, PageVO<PostVO> vo) {
        try {
            redis.opsForValue().set(String.format(HOT_KEY, page, size),
                    objectMapper.writeValueAsString(vo), jitter(HOT_TTL));
        } catch (Exception e) {
            log.warn("post hot cache write failed, page={}, size={}", page, size, e);
        }
    }

    public void evictDetail(Long id) {
        try {
            redis.delete(String.format(DETAIL_KEY, id));
        } catch (Exception e) {
            log.warn("post detail cache evict failed, id={}", id, e);
        }
    }

    /** SCAN 清全部热榜分页缓存（避免 KEYS 阻塞） */
    public void evictHotAll() {
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
                .match(HOT_KEY_PREFIX + "*").count(200).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                redis.delete(Objects.requireNonNull(key));
            }
        } catch (Exception e) {
            log.warn("post hot cache evict failed", e);
        }
    }

    /** SETNX 抢重建锁；Redis 不可用时视为抢到（直接回源，不阻塞业务） */
    public boolean tryLockDetail(Long id) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue()
                    .setIfAbsent(String.format(DETAIL_LOCK_KEY, id), "1", LOCK_TTL));
        } catch (Exception e) {
            log.warn("post detail cache lock unavailable, id={}", id, e);
            return true;
        }
    }

    public void unlockDetail(Long id) {
        try {
            redis.delete(String.format(DETAIL_LOCK_KEY, id));
        } catch (Exception e) {
            log.warn("post detail cache unlock failed, id={}", id, e);
        }
    }

    /** 其他线程重建期间的短暂等待：一旦缓存出现（含空值哨兵）立即返回 */
    private Lookup<PostVO> awaitDetail(Long id) {
        for (int i = 0; i < WAIT_RETRY; i++) {
            if (!sleep(WAIT_INTERVAL_MILLIS)) return Lookup.missing();
            Lookup<PostVO> lookup = getDetail(id);
            if (!lookup.miss()) return lookup;
        }
        return Lookup.missing();
    }

    /** @return false 表示线程被中断 */
    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** TTL 随机抖动 0~10%，避免同批缓存同时失效（雪崩） */
    private Duration jitter(Duration base) {
        long millis = base.toMillis();
        return Duration.ofMillis(millis + ThreadLocalRandom.current().nextLong(millis / 10 + 1));
    }
}