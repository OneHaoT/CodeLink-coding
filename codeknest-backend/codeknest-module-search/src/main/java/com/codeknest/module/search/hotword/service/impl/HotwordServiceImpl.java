package com.codeknest.module.search.hotword.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeknest.module.search.hotword.entity.SearchHotword;
import com.codeknest.module.search.hotword.mapper.SearchHotwordMapper;
import com.codeknest.module.search.hotword.service.HotwordService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 搜索热词实现 — 「MySQL 权威 + Redis 关口」两段式：
 * <ul>
 *   <li>写：搜索只累加 Redis 增量（ZINCRBY，不打 DB），定时任务批量 upsert 落库后按增量回扣；</li>
 *   <li>读：先读 Redis TopN 缓存，未命中回源 MySQL 并回填；</li>
 *   <li>容错：Redis 异常降级为直接落库；落库失败保留增量下轮重试；任何异常都不影响搜索主流程。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotwordServiceImpl implements HotwordService {

    /** Redis 未落库增量（member=关键词，score=待落库增量次数） */
    private static final String PENDING_KEY = "search:hotword:pending";
    private static final String TOP_KEY_PREFIX = "search:hotword:top:";
    private static final String TOP_KEY = TOP_KEY_PREFIX + "%d";

    /** 与 t_search_hotword.keyword 列宽一致，超长关键词不入统计 */
    private static final int MAX_KEYWORD_LENGTH = 64;
    private static final int MAX_FLUSH_BATCH = 500;
    private static final Duration PENDING_TTL = Duration.ofDays(7);
    private static final Duration TOP_TTL = Duration.ofMinutes(5);

    private final SearchHotwordMapper hotwordMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public void record(String keyword) {
        if (!StringUtils.hasText(keyword)) return;
        String word = keyword.trim();
        if (word.isEmpty() || word.length() > MAX_KEYWORD_LENGTH) return;
        try {
            redis.opsForZSet().incrementScore(PENDING_KEY, word, 1);
            redis.expire(PENDING_KEY, PENDING_TTL);
        } catch (Exception e) {
            // 缓存不可用不能让搜索行为丢失：直接落库兜底（MySQL 始终是权威源）
            log.warn("搜索热词增量累加失败，改为直接落库", e);
            upsertDirectly(word);
        }
    }

    @Override
    public List<String> topN(int topN) {
        int size = Math.max(topN, 1);
        String cacheKey = String.format(TOP_KEY, size);
        try {
            String json = redis.opsForValue().get(cacheKey);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {
            log.warn("搜索热词读缓存不可用，回源 MySQL", e);
        }

        List<String> words = loadTopFromDb(size);
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(words), jitter(TOP_TTL));
        } catch (Exception e) {
            log.warn("搜索热词读缓存回填失败", e);
        }
        return words;
    }

    @Override
    @Scheduled(fixedDelay = 3 * 60 * 1000L, initialDelay = 60 * 1000L)
    public int flushToDb() {
        Set<ZSetOperations.TypedTuple<String>> tuples;
        try {
            tuples = redis.opsForZSet().rangeWithScores(PENDING_KEY, 0, MAX_FLUSH_BATCH - 1);
        } catch (Exception e) {
            log.warn("搜索热词增量读取失败，本轮跳过落库", e);
            return 0;
        }
        if (tuples == null || tuples.isEmpty()) return 0;

        LocalDateTime now = LocalDateTime.now();
        List<SearchHotword> rows = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String word = tuple.getValue();
            Double score = tuple.getScore();
            if (word == null || score == null) continue;
            long delta = Math.round(score);
            if (delta <= 0) continue;
            rows.add(new SearchHotword(word, delta, now));
        }
        if (rows.isEmpty()) {
            dropNonPositiveMembers();
            return 0;
        }

        try {
            hotwordMapper.upsertBatch(rows);
        } catch (Exception e) {
            // 落库失败：增量原样保留，下一轮重试，Redis 与 MySQL 都不丢数据
            log.warn("搜索热词落库失败，增量保留待下轮重试，size={}", rows.size(), e);
            return 0;
        }

        // 落库成功后按增量回扣：期间并发新增的计数仍在增量里，不会被误清
        for (SearchHotword row : rows) {
            try {
                redis.opsForZSet().incrementScore(PENDING_KEY, row.getKeyword(), -row.getSearchCount());
            } catch (Exception e) {
                log.warn("搜索热词增量回扣失败，本轮计数下轮将重复累加", e);
                break;
            }
        }
        dropNonPositiveMembers();
        evictTopCache();
        log.info("搜索热词落库完成，关键词数={}", rows.size());
        return rows.size();
    }

    /** Redis 兜底不可用时的单条直落（计数 +1） */
    private void upsertDirectly(String keyword) {
        try {
            hotwordMapper.upsertBatch(List.of(new SearchHotword(keyword, 1L, LocalDateTime.now())));
            evictTopCache();
        } catch (Exception e) {
            log.warn("搜索热词直接落库失败，本次计数丢弃", e);
        }
    }

    private List<String> loadTopFromDb(int size) {
        try {
            Page<SearchHotword> page = hotwordMapper.selectPage(new Page<>(1, size),
                    new LambdaQueryWrapper<SearchHotword>()
                            .orderByDesc(SearchHotword::getSearchCount)
                            .orderByDesc(SearchHotword::getLastSearchedAt));
            return page.getRecords().stream()
                    .map(SearchHotword::getKeyword)
                    .filter(StringUtils::hasText)
                    .toList();
        } catch (Exception e) {
            log.warn("搜索热词回源 MySQL 失败，返回空列表", e);
            return List.of();
        }
    }

    /** 清理回扣后归零 / 为负的增量成员 */
    private void dropNonPositiveMembers() {
        try {
            redis.opsForZSet().removeRangeByScore(PENDING_KEY, Double.NEGATIVE_INFINITY, 0);
        } catch (Exception e) {
            log.warn("搜索热词增量清理失败", e);
        }
    }

    /** SCAN 清全部 TopN 读缓存（避免 KEYS 阻塞） */
    private void evictTopCache() {
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions()
                .match(TOP_KEY_PREFIX + "*").count(200).build())) {
            while (cursor.hasNext()) {
                redis.delete(Objects.requireNonNull(cursor.next()));
            }
        } catch (Exception e) {
            log.warn("搜索热词读缓存清理失败", e);
        }
    }

    /** TTL 随机抖动 0~10%，避免同批缓存同时失效（雪崩） */
    private Duration jitter(Duration base) {
        long millis = base.toMillis();
        return Duration.ofMillis(millis + ThreadLocalRandom.current().nextLong(millis / 10 + 1));
    }
}