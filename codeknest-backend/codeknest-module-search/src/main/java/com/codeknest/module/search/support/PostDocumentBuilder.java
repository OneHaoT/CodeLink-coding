package com.codeknest.module.search.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MySQL 文章实体 → ES 检索文档 转换器（批量预取，避免 N+1 查询）
 */
@Component
@RequiredArgsConstructor
public class PostDocumentBuilder {

    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final PostTagMapper postTagMapper;
    private final TagMapper tagMapper;

    public PostDocument build(Post post) {
        if (post == null) return null;
        List<PostDocument> docs = buildAll(List.of(post));
        return docs.isEmpty() ? null : docs.get(0);
    }

    public List<PostDocument> buildAll(List<Post> posts) {
        if (posts == null || posts.isEmpty()) return Collections.emptyList();

        Set<Long> userIds = posts.stream().map(Post::getUserId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> categoryIds = posts.stream().map(Post::getCategoryId).filter(Objects::nonNull)
                .map(Integer::longValue).collect(Collectors.toSet());
        List<Long> postIds = posts.stream().map(Post::getId).toList();

        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Category> categoryMap = categoryIds.isEmpty() ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));

        Map<Long, List<String>> tagsByPost = new HashMap<>();
        List<PostTag> rels = postTagMapper.selectList(
                new LambdaQueryWrapper<PostTag>().in(PostTag::getPostId, postIds));
        if (!rels.isEmpty()) {
            Map<Long, Tag> tagMap = tagMapper.selectBatchIds(
                            rels.stream().map(PostTag::getTagId).distinct().toList()).stream()
                    .collect(Collectors.toMap(Tag::getId, t -> t));
            for (PostTag rel : rels) {
                Tag tag = tagMap.get(rel.getTagId());
                if (tag != null) {
                    tagsByPost.computeIfAbsent(rel.getPostId(), k -> new ArrayList<>()).add(tag.getName());
                }
            }
        }

        return posts.stream().map(p -> {
            PostDocument doc = new PostDocument();
            doc.setId(p.getId());
            doc.setUserId(p.getUserId());
            User author = p.getUserId() == null ? null : userMap.get(p.getUserId());
            doc.setUsername(author == null ? null : author.getUsername());
            doc.setTitle(p.getTitle());
            doc.setSummary(p.getSummary());
            doc.setContent(p.getContent());
            doc.setCategoryId(p.getCategoryId());
            Category category = p.getCategoryId() == null ? null : categoryMap.get(p.getCategoryId().longValue());
            doc.setCategoryName(category == null ? null : category.getName());
            doc.setTags(tagsByPost.getOrDefault(p.getId(), Collections.emptyList()));
            doc.setViewCount(p.getViewCount());
            doc.setLikeCount(p.getLikeCount());
            doc.setPublishedAt(toInstant(p.getPublishedAt()));
            return doc;
        }).toList();
    }

    /** LocalDateTime → Instant（ES date 字段统一用 epoch millis，避免时区/格式往返问题） */
    private Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant();
    }
}
