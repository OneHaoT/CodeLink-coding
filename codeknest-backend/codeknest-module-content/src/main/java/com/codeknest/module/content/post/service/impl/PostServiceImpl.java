package com.codeknest.module.content.post.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.content.post.cache.PostCacheService;
import com.codeknest.module.content.post.dto.PostQueryDTO;
import com.codeknest.module.content.post.dto.SaveDraftDTO;
import com.codeknest.module.content.post.dto.SavePostDTO;
import com.codeknest.module.content.post.entity.Category;
import com.codeknest.module.content.post.entity.Post;
import com.codeknest.module.content.post.entity.PostDraftDocument;
import com.codeknest.module.content.post.entity.PostTag;
import com.codeknest.module.content.post.entity.Tag;
import com.codeknest.module.content.post.mapper.CategoryMapper;
import com.codeknest.module.content.post.mapper.PostMapper;
import com.codeknest.module.content.post.mapper.PostTagMapper;
import com.codeknest.module.content.post.mapper.TagMapper;
import com.codeknest.module.content.post.mq.PostSyncProducer;
import com.codeknest.module.content.post.repository.PostDraftMongoRepository;
import com.codeknest.module.content.post.service.ImageStorageService;
import com.codeknest.module.content.post.service.PostService;
import com.codeknest.module.content.post.spi.PostInteractionQuery;
import com.codeknest.module.content.post.support.SensitiveWordChecker;
import com.codeknest.module.content.post.vo.DraftVO;
import com.codeknest.module.content.post.vo.PostVO;
import com.codeknest.module.account.user.entity.UserProfile;
import com.codeknest.module.account.user.mapper.UserProfileMapper;
import com.codeknest.module.account.user.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final PostTagMapper postTagMapper;
    private final PostDraftMongoRepository draftMongoRepository;
    private final UserMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final UserService userService;
    private final ImageStorageService imageStorageService;
    private final PostSyncProducer postSyncProducer;
    private final SensitiveWordChecker sensitiveWordChecker;
    private final PostCacheService postCacheService;
    private final ObjectMapper objectMapper;
    /** 互动模块 SPI（可选 Bean） */
    private final ObjectProvider<PostInteractionQuery> interactionQueryProvider;

    // ==================== 文章 ====================

    @Override
    public PageVO<PostVO> list(PostQueryDTO q, Long currentUserId) {
        long size = Math.min(q.getSize() == null ? 10 : Math.max(q.getSize(), 1), 50);
        long pageNo = q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage();
        boolean hotSort = "hot".equalsIgnoreCase(q.getSort());
        // 全站热榜（无个人/分类/标签/关键词过滤）才走缓存，避免长尾 key
        boolean cacheable = hotSort && q.getUserId() == null && q.getCategoryId() == null
                && !StringUtils.hasText(q.getQ()) && !StringUtils.hasText(q.getTag());

        if (cacheable) {
            PageVO<PostVO> cached = postCacheService.getHot(pageNo, size);
            if (cached != null) {
                applyPersonalization(cached.getItems(), currentUserId);
                return cached;
            }
        }

        Page<Post> page = new Page<>(pageNo, size);

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, 1);
        if (q.getUserId() != null) {
            wrapper.eq(Post::getUserId, q.getUserId());
        }
        if (q.getCategoryId() != null) {
            wrapper.eq(Post::getCategoryId, q.getCategoryId());
        }
        if (StringUtils.hasText(q.getQ())) {
            String kw = q.getQ().trim();
            wrapper.and(w -> w.like(Post::getTitle, kw).or().like(Post::getSummary, kw));
        }
        if (StringUtils.hasText(q.getTag())) {
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, q.getTag().trim()));
            if (tag == null) {
                return PageVO.of(page, Collections.<PostVO>emptyList());
            }
            wrapper.inSql(Post::getId,
                    "SELECT post_id FROM t_post_tag WHERE tag_id = " + tag.getId());
        }
        if (hotSort) {
            wrapper.orderByDesc(Post::getViewCount).orderByDesc(Post::getLikeCount);
        } else {
            wrapper.orderByDesc(Post::getPublishedAt).orderByDesc(Post::getId);
        }

        Page<Post> result = postMapper.selectPage(page, wrapper);
        List<PostVO> vos = enrich(result.getRecords(), currentUserId, false);
        PageVO<PostVO> pageVO = PageVO.of(result, vos);
        if (cacheable) {
            PageVO<PostVO> publicPage = objectMapper.convertValue(pageVO, new TypeReference<PageVO<PostVO>>() {});
            publicPage.getItems().forEach(this::clearPersonalization);
            postCacheService.putHot(pageNo, size, publicPage);
        }
        return pageVO;
    }

    @Override
    public List<PostVO> listByIdsOrdered(List<Long> ids, Long currentUserId) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<Post> posts = postMapper.selectBatchIds(ids).stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .toList();
        Map<Long, Post> map = posts.stream().collect(Collectors.toMap(Post::getId, p -> p));
        List<Post> ordered = ids.stream().map(map::get).filter(Objects::nonNull).toList();
        return enrich(ordered, currentUserId, false);
    }

    @Override
    public PostVO detail(Long id, Long currentUserId) {
        // 浏览量 +1（缓存命中也保留业务语义）
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, id)
                .setSql("view_count = view_count + 1"));

        PostVO cached = postCacheService.getDetail(id);
        if (cached != null) {
            applyPersonalization(List.of(cached), currentUserId);
            return cached;
        }

        Post post = postMapper.selectById(id);
        if (post == null || post.getStatus() == null || post.getStatus() != 1) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        PostVO vo = enrich(List.of(post), currentUserId, true).get(0);
        // 缓存"公共"副本：个性化字段清零后存储
        PostVO publicCopy = objectMapper.convertValue(vo, PostVO.class);
        clearPersonalization(publicCopy);
        postCacheService.putDetail(id, publicCopy);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPost(Long userId, SavePostDTO dto) {
        sensitiveWordChecker.check(dto.getTitle(), dto.getSummary(), dto.getContent());
        Post post = new Post();
        BeanUtils.copyProperties(dto, post);
        if (!StringUtils.hasText(post.getSummary()) && StringUtils.hasText(dto.getContent())) {
            String plain = dto.getContent().replaceAll("[#>*`\\-\\n!\\[\\]()]", " ").trim();
            post.setSummary(plain.length() > 120 ? plain.substring(0, 120) : plain);
        }
        post.setUserId(userId);
        post.setStatus(1);
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setFavoriteCount(0);
        post.setPublishedAt(LocalDateTime.now());
        postMapper.insert(post);

        // 确认正文临时图片，迁移到正式目录并改写 URL；封面自动取正文第一张图
        String confirmedContent = imageStorageService.confirmImages(post.getContent(), String.valueOf(post.getId()));
        String derivedCover = extractFirstImageUrl(confirmedContent);
        if (!Objects.equals(confirmedContent, post.getContent())
                || !Objects.equals(derivedCover, post.getCoverImage())) {
            post.setContent(confirmedContent);
            post.setCoverImage(derivedCover);
            postMapper.updateById(post);
        }

        List<Tag> tags = resolveTags(dto.getTagIds(), dto.getTagNames());
        bindTags(post.getId(), tags);
        recalcTagCount(tags);

        ensureProfile(userId);
        profileMapper.update(null, new LambdaUpdateWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId).setSql("posts_count = posts_count + 1"));
        postSyncProducer.send(post.getId());
        postCacheService.evictHotAll();
        return post.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePost(Long userId, Long id, SavePostDTO dto) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        boolean isAdmin = "ROLE_ADMIN".equals(SecurityContext.getRole());
        if (!isAdmin && !post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_OWNER);
        }
        sensitiveWordChecker.check(dto.getTitle(), dto.getSummary(), dto.getContent());
        post.setTitle(dto.getTitle());
        post.setSummary(dto.getSummary());
        // 确认本次编辑新上传的临时图片；封面自动取正文第一张图
        post.setContent(imageStorageService.confirmImages(dto.getContent(), String.valueOf(id)));
        post.setCoverImage(extractFirstImageUrl(post.getContent()));
        post.setCategoryId(dto.getCategoryId());
        postMapper.updateById(post);

        if (dto.getTagIds() != null || dto.getTagNames() != null) {
            List<Tag> oldTags = listTagsByPost(id);
            postTagMapper.delete(new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, id));
            List<Tag> tags = resolveTags(dto.getTagIds(), dto.getTagNames());
            bindTags(id, tags);
            Set<Tag> affected = new HashSet<>();
            affected.addAll(oldTags);
            affected.addAll(tags);
            recalcTagCount(new ArrayList<>(affected));
        }
        postSyncProducer.send(id);
        postCacheService.evictDetail(id);
        postCacheService.evictHotAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long userId, Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        boolean isAdmin = "ROLE_ADMIN".equals(SecurityContext.getRole());
        if (!isAdmin && !post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_OWNER);
        }
        postMapper.deleteById(id);
        List<Tag> tags = listTagsByPost(id);
        postTagMapper.delete(new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, id));
        recalcTagCount(tags);
        profileMapper.update(null, new LambdaUpdateWrapper<UserProfile>()
                .eq(UserProfile::getUserId, post.getUserId())
                .setSql("posts_count = GREATEST(posts_count - 1, 0)"));
        postSyncProducer.send(id);
        postCacheService.evictDetail(id);
        postCacheService.evictHotAll();
    }

    // ==================== 草稿（MongoDB） ====================

    @Override
    public List<DraftVO> listDrafts(Long userId) {
        return draftMongoRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toDraftVO).toList();
    }

    @Override
    public DraftVO draftDetail(Long userId, String id) {
        PostDraftDocument draft = draftMongoRepository.findById(id).orElse(null);
        if (draft == null || !draft.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "草稿不存在");
        }
        return toDraftVO(draft);
    }

    @Override
    public String saveDraft(Long userId, SaveDraftDTO dto) {
        PostDraftDocument draft;
        boolean isNew;
        if (StringUtils.hasText(dto.getId())) {
            draft = draftMongoRepository.findById(dto.getId()).orElse(null);
            if (draft == null || !draft.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "草稿不存在");
            }
            isNew = false;
        } else {
            draft = new PostDraftDocument();
            draft.setUserId(userId);
            draft.setCreatedAt(LocalDateTime.now());
            isNew = true;
        }
        draft.setPostId(dto.getPostId());
        draft.setTitle(dto.getTitle());
        draft.setSummary(dto.getSummary());
        draft.setCategoryId(dto.getCategoryId());
        draft.setTagNames(dto.getTagNames() != null ? String.join(",", dto.getTagNames()) : null);
        draft.setUpdatedAt(LocalDateTime.now());
        if (isNew) {
            draft.setContent(dto.getContent());
            draftMongoRepository.save(draft);
            // 保存后拿到 id，确认正文临时图片并改写 URL；封面自动取正文第一张图
            String confirmedContent = imageStorageService.confirmImages(draft.getContent(), draft.getId());
            draft.setContent(confirmedContent);
            draft.setCoverImage(extractFirstImageUrl(confirmedContent));
            draftMongoRepository.save(draft);
        } else {
            draft.setContent(imageStorageService.confirmImages(dto.getContent(), draft.getId()));
            draft.setCoverImage(extractFirstImageUrl(draft.getContent()));
            draftMongoRepository.save(draft);
        }
        return draft.getId();
    }

    @Override
    public void deleteDraft(Long userId, String id) {
        PostDraftDocument draft = draftMongoRepository.findById(id).orElse(null);
        if (draft == null || !draft.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "草稿不存在");
        }
        draftMongoRepository.deleteById(id);
    }

    // ==================== private ====================

    /** Markdown 图片语法：![alt](url) */
    private static final Pattern MD_IMAGE_PATTERN =
            Pattern.compile("!\\[[^\\]]*\\]\\(([^)\\s]+)[^)]*\\)");

    /** HTML img 标签：<img src="url"> */
    private static final Pattern HTML_IMG_PATTERN =
            Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    /** 封面规则：正文第一张图即封面图 */
    private String extractFirstImageUrl(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        Matcher md = MD_IMAGE_PATTERN.matcher(content);
        if (md.find()) {
            return md.group(1);
        }
        Matcher html = HTML_IMG_PATTERN.matcher(content);
        return html.find() ? html.group(1) : null;
    }

    private void ensureProfile(Long userId) {
        if (profileMapper.selectById(userId) == null) {
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            p.setFollowersCount(0);
            p.setFollowingCount(0);
            p.setPostsCount(0);
            profileMapper.insert(p);
        }
    }

    private List<Tag> resolveTags(List<Long> tagIds, List<String> tagNames) {
        Map<Long, Tag> byId = new LinkedHashMap<>();
        if (tagIds != null) {
            for (Tag t : tagMapper.selectBatchIds(tagIds)) {
                byId.put(t.getId(), t);
            }
        }
        if (tagNames != null) {
            for (String raw : tagNames) {
                if (!StringUtils.hasText(raw)) continue;
                String name = raw.trim();
                if (name.length() > 32) name = name.substring(0, 32);
                Tag exist = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name));
                if (exist != null) {
                    byId.putIfAbsent(exist.getId(), exist);
                    continue;
                }
                Tag tag = new Tag();
                tag.setName(name);
                tag.setPostCount(0);
                String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-|-$)", "");
                if (slug.isBlank()) {
                    // 中文等非 ASCII 标签名兜底，保证 slug 非空且基本唯一
                    slug = "tag-" + Integer.toHexString(name.hashCode())
                            + "-" + java.util.UUID.randomUUID().toString().substring(0, 6);
                }
                tag.setSlug(slug);
                tagMapper.insert(tag);
                byId.put(tag.getId(), tag);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private void bindTags(Long postId, List<Tag> tags) {
        for (Tag t : tags) {
            PostTag pt = new PostTag();
            pt.setPostId(postId);
            pt.setTagId(t.getId());
            postTagMapper.insert(pt);
        }
    }

    private List<Tag> listTagsByPost(Long postId) {
        List<PostTag> rels = postTagMapper.selectList(
                new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId));
        if (rels.isEmpty()) return Collections.emptyList();
        return tagMapper.selectBatchIds(rels.stream().map(PostTag::getTagId).toList());
    }

    private void recalcTagCount(List<Tag> tags) {
        for (Tag t : tags) {
            long count = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                    .eq(Post::getStatus, 1)
                    .inSql(Post::getId, "SELECT post_id FROM t_post_tag WHERE tag_id = " + t.getId()));
            t.setPostCount((int) count);
            tagMapper.updateById(t);
        }
    }

    private List<PostVO> enrich(List<Post> posts, Long currentUserId, boolean withContent) {
        if (posts.isEmpty()) return Collections.emptyList();

        Set<Long> userIds = posts.stream().map(Post::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> categoryIds = posts.stream().map(Post::getCategoryId).filter(Objects::nonNull)
                .map(Integer::longValue).collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Category> categoryMap = categoryIds.isEmpty() ? Collections.emptyMap()
                : categoryMapper.selectBatchIds(categoryIds).stream().collect(Collectors.toMap(Category::getId, c -> c));

        List<PostTag> allRels = postTagMapper.selectList(new LambdaQueryWrapper<PostTag>()
                .in(PostTag::getPostId, posts.stream().map(Post::getId).toList()));
        Map<Long, List<Tag>> tagsByPost = new HashMap<>();
        if (!allRels.isEmpty()) {
            Map<Long, Tag> tagMap = tagMapper.selectBatchIds(
                            allRels.stream().map(PostTag::getTagId).distinct().toList()).stream()
                    .collect(Collectors.toMap(Tag::getId, t -> t));
            for (PostTag rel : allRels) {
                Tag t = tagMap.get(rel.getTagId());
                if (t != null) {
                    tagsByPost.computeIfAbsent(rel.getPostId(), k -> new ArrayList<>()).add(t);
                }
            }
        }

        PostInteractionQuery interaction = interactionQueryProvider.getIfAvailable();

        return posts.stream().map(p -> {
            User author = userMap.get(p.getUserId());
            Category category = p.getCategoryId() == null ? null : categoryMap.get(p.getCategoryId().longValue());
            boolean liked = interaction != null && currentUserId != null && interaction.isLiked(currentUserId, p.getId());
            boolean favorited = interaction != null && currentUserId != null && interaction.isFavorited(currentUserId, p.getId());
            boolean followingAuthor = currentUserId != null && author != null
                    && !currentUserId.equals(author.getId())
                    && userService.isFollowing(currentUserId, author.getId());
            PostVO.PostVOBuilder b = PostVO.builder()
                    .id(p.getId())
                    .title(p.getTitle())
                    .summary(p.getSummary())
                    .coverImage(p.getCoverImage())
                    .author(author == null ? null : PostVO.Author.builder()
                            .id(author.getId()).username(author.getUsername()).avatar(author.getAvatar()).build())
                    .category(category == null ? null : PostVO.CategoryInfo.builder()
                            .id(Math.toIntExact(category.getId())).name(category.getName()).build())
                    .tags(tagsByPost.getOrDefault(p.getId(), Collections.emptyList()).stream()
                            .map(t -> new PostVO.TagInfo(t.getId(), t.getName())).toList())
                    .viewCount(p.getViewCount())
                    .likeCount(p.getLikeCount())
                    .commentCount(p.getCommentCount())
                    .favoriteCount(p.getFavoriteCount())
                    .isLiked(liked)
                    .isFavorited(favorited)
                    .isFollowingAuthor(followingAuthor)
                    .publishedAt(p.getPublishedAt())
                    .createdAt(p.getCreatedAt());
            if (withContent) b.content(p.getContent());
            return b.build();
        }).toList();
    }

    /** 缓存命中后按登录态补齐个性化字段（与 enrich 内联逻辑一致） */
    private void applyPersonalization(List<PostVO> vos, Long currentUserId) {
        if (currentUserId == null) return;
        PostInteractionQuery interaction = interactionQueryProvider.getIfAvailable();
        for (PostVO vo : vos) {
            vo.setIsLiked(interaction != null && interaction.isLiked(currentUserId, vo.getId()));
            vo.setIsFavorited(interaction != null && interaction.isFavorited(currentUserId, vo.getId()));
            PostVO.Author author = vo.getAuthor();
            vo.setIsFollowingAuthor(author != null
                    && !currentUserId.equals(author.getId())
                    && userService.isFollowing(currentUserId, author.getId()));
        }
    }

    private void clearPersonalization(PostVO vo) {
        vo.setIsLiked(false);
        vo.setIsFavorited(false);
        vo.setIsFollowingAuthor(false);
    }

    private DraftVO toDraftVO(PostDraftDocument d) {
        DraftVO vo = new DraftVO();
        BeanUtils.copyProperties(d, vo);
        if (d.getTagNames() != null && !d.getTagNames().isBlank()) {
            vo.setTagNames(java.util.Arrays.stream(d.getTagNames().split(","))
                    .filter(s -> !s.isBlank()).toList());
        }
        return vo;
    }
}
