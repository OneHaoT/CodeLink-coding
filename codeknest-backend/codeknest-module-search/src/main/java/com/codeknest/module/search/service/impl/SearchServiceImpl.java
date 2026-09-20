package com.codeknest.module.search.service.impl;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.content.post.entity.Post;
import com.codeknest.module.content.post.mapper.PostMapper;
import com.codeknest.module.search.document.PostDocument;
import com.codeknest.module.search.hotword.service.HotwordService;
import com.codeknest.module.search.service.SearchService;
import com.codeknest.module.search.support.PostDocumentBuilder;
import com.codeknest.module.search.vo.SearchVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 检索服务实现 — 基于 Spring Data Elasticsearch（IK 分词 + 服务端高亮）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ElasticsearchOperations operations;
    private final PostMapper postMapper;
    private final PostDocumentBuilder documentBuilder;
    private final HotwordService hotwordService;

    @Override
    public PageVO<SearchVO> search(String q, Integer page, Integer size, String sort) {
        int pageNo = page == null || page < 1 ? 1 : page;
        int pageSize = size == null ? 10 : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        PageVO<SearchVO> result = new PageVO<>();
        result.setPage(pageNo);
        result.setSize(pageSize);
        result.setItems(List.of());
        result.setTotal(0);
        result.setTotalPages(0);
        if (!StringUtils.hasText(q)) {
            return result;
        }
        String keyword = q.trim();

        HighlightQuery highlightQuery = new HighlightQuery(
                new Highlight(
                        HighlightParameters.builder().withPreTags("<em>").withPostTags("</em>").build(),
                        List.of(new HighlightField("title"), new HighlightField("summary"))),
                PostDocument.class);

        NativeQueryBuilder builder = NativeQuery.builder()
                .withQuery(b -> b.multiMatch(m -> m
                        .query(keyword)
                        // tags.text 为标签的分词子字段，使「按标签搜索」与标题/摘要/正文一致生效
                        .fields("title^3", "summary^2", "content", "tags.text^2")
                        .type(TextQueryType.MostFields)))
                .withPageable(PageRequest.of(pageNo - 1, pageSize))
                .withHighlightQuery(highlightQuery);
        List<SortOptions> sorts = sortOptions(sort);
        if (!sorts.isEmpty()) {
            builder.withSort(sorts);
        }

        SearchHits<PostDocument> hits = operations.search(builder.build(), PostDocument.class);
        long total = hits.getTotalHits();
        result.setItems(hits.getSearchHits().stream().map(this::toVO).toList());
        result.setTotal(total);
        result.setTotalPages((total + pageSize - 1) / pageSize);
        recordHotword(keyword);
        return result;
    }

    @Override
    public List<String> hotwords(int topN) {
        return hotwordService.topN(topN);
    }

    /** 有效搜索记入热词排行（Redis 累加增量，定时批量落库；失败只留日志不影响搜索） */
    private void recordHotword(String keyword) {
        try {
            hotwordService.record(keyword);
        } catch (Exception e) {
            log.warn("搜索热词记录失败，keyword={}", keyword, e);
        }
    }

    @Override
    public void syncPost(Long postId) {
        if (postId == null) return;
        try {
            Post post = postMapper.selectById(postId);
            if (post == null || post.getStatus() == null || post.getStatus() != 1) {
                operations.delete(String.valueOf(postId), PostDocument.class);
                return;
            }
            PostDocument doc = documentBuilder.build(post);
            if (doc != null) {
                operations.save(doc);
                // 单篇增量同步后立即刷新，保证「发布后马上搜索」可见（全量重建走批量，无需刷新）
                operations.indexOps(PostDocument.class).refresh();
            }
        } catch (Exception e) {
            // 不向上抛出，避免 MQ 消息无限重投；ES 异常时以日志留痕，可由 reindex 兜底
            log.warn("同步文章到 ES 失败，postId={}", postId, e);
        }
    }

    @Override
    public long reindex() {
        IndexOperations indexOps = operations.indexOps(PostDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.createWithMapping();

        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>().eq(Post::getStatus, 1));
        if (posts.isEmpty()) {
            log.info("全量重建 ES 索引完成，无已发布文章");
            return 0;
        }
        List<PostDocument> docs = documentBuilder.buildAll(posts);
        operations.save(docs);
        log.info("全量重建 ES 索引完成，写入文档数={}", docs.size());
        return docs.size();
    }

    private List<SortOptions> sortOptions(String sort) {
        if ("time".equalsIgnoreCase(sort)) {
            return List.of(SortOptions.of(s -> s.field(f -> f.field("publishedAt").order(SortOrder.Desc))));
        }
        if ("views".equalsIgnoreCase(sort)) {
            return List.of(SortOptions.of(s -> s.field(f -> f.field("viewCount").order(SortOrder.Desc))));
        }
        return List.of();
    }

    private SearchVO toVO(SearchHit<PostDocument> hit) {
        PostDocument doc = hit.getContent();
        Map<String, List<String>> highlights = hit.getHighlightFields();
        return SearchVO.builder()
                .id(doc.getId())
                .title(firstFragment(highlights, "title", doc.getTitle()))
                .summary(firstFragment(highlights, "summary", doc.getSummary()))
                .author(doc.getUserId() == null ? null : SearchVO.Author.builder()
                        .id(doc.getUserId())
                        .username(doc.getUsername())
                        .build())
                .tags(doc.getTags())
                .viewCount(doc.getViewCount())
                .publishedAt(toLocalDateTime(doc.getPublishedAt()))
                .build();
    }

    /** Instant → LocalDateTime */
    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private String firstFragment(Map<String, List<String>> highlights, String field, String fallback) {
        List<String> fragments = highlights.get(field);
        return fragments == null || fragments.isEmpty() ? fallback : fragments.get(0);
    }
}
