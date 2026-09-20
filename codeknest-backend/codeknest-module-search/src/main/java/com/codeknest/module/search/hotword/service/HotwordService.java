package com.codeknest.module.search.hotword.service;

import java.util.List;

/**
 * 搜索热词服务 — MySQL 为权威源（t_search_hotword），Redis 只做增量累加与读加速
 */
public interface HotwordService {

    /** 记录一次有效搜索（只累加 Redis 增量，不打 DB；Redis 不可用时降级直接落库） */
    void record(String keyword);

    /** 热词 TopN（先读 Redis 缓存，未命中回源 MySQL 并回填） */
    List<String> topN(int topN);

    /** 把 Redis 增量批量落库到 MySQL，返回本轮落库的关键词数（定时调用，失败保留增量待重试） */
    int flushToDb();
}