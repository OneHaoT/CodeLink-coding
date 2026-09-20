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

/**
 * 文章热点数据缓存 — 详情 / 热榜（仅缓存"公共 PostVO"，个性化字段由调用方按登录态补齐）
 * 序列化失败一律降级直查 DB，不影响业务主流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostCacheService {

    private static final String DETAIL_KEY = "post:detail:%d";
    private static final String HOT_KEY_PREFIX = "post:hot:";
    private static final String HOT_KEY = HOT_KEY_PREFIX + "%d:%d";
    private static final Duration DETAIL_TTL = Duration.ofMinutes(30);
    private static final Duration HOT_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public PostVO getDetail(Long id) {
        String json = redis.opsForValue().get(String.format(DETAIL_KEY, id));
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, PostVO.class);
        } catch (Exception e) {
            log.warn("post detail cache deserialize failed, id={}", id, e);
            return null;
        }
    }

    public void putDetail(Long id, PostVO vo) {
        try {
            redis.opsForValue().set(String.format(DETAIL_KEY, id),
                    objectMapper.writeValueAsString(vo), DETAIL_TTL);
        } catch (Exception e) {
            log.warn("post detail cache serialize failed, id={}", id, e);
        }
    }

    public PageVO<PostVO> getHot(long page, long size) {
        String json = redis.opsForValue().get(String.format(HOT_KEY, page, size));
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
                    objectMapper.writeValueAsString(vo), HOT_TTL);
        } catch (Exception e) {
            log.warn("post hot cache serialize failed, page={}, size={}", page, size, e);
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
}
