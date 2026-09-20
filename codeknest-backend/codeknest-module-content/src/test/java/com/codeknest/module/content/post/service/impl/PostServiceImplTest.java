package com.codeknest.module.content.post.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.content.post.cache.PostCacheService;
import com.codeknest.module.content.post.dto.PostQueryDTO;
import com.codeknest.module.content.post.dto.SaveDraftDTO;
import com.codeknest.module.content.post.dto.SavePostDTO;
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
import com.codeknest.module.content.post.spi.PostInteractionQuery;
import com.codeknest.module.content.post.support.SensitiveWordChecker;
import com.codeknest.module.content.post.vo.PostVO;
import com.codeknest.module.account.user.entity.UserProfile;
import com.codeknest.module.account.user.mapper.UserProfileMapper;
import com.codeknest.module.account.user.service.UserService;
import com.codeknest.module.account.event.UserActions;
import com.codeknest.module.account.event.UserEventMessage;
import com.codeknest.module.account.event.UserEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 文章服务单元测试 — 纯 Mockito，不启动 Spring、不连中间件
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class PostServiceImplTest {

    private static final Long AUTHOR_ID = 1L;
    private static final Long POST_ID = 77L;

    @Mock
    private PostMapper postMapper;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private PostTagMapper postTagMapper;
    @Mock
    private PostDraftMongoRepository draftMongoRepository;
    @Mock
    private com.codeknest.module.account.auth.mapper.UserMapper userMapper;
    @Mock
    private UserProfileMapper profileMapper;
    @Mock
    private UserService userService;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private PostSyncProducer postSyncProducer;
    @Mock
    private SensitiveWordChecker sensitiveWordChecker;
    @Mock
    private PostCacheService postCacheService;
    @Mock
    private UserEventPublisher eventPublisher;
    @Mock
    private ObjectProvider<PostInteractionQuery> interactionQueryProvider;
    @Mock
    private PostInteractionQuery interactionQuery;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PostServiceImpl postService;

    @BeforeEach
    void setUp() {
        postService = new PostServiceImpl(postMapper, tagMapper, categoryMapper, postTagMapper,
                draftMongoRepository, userMapper, profileMapper, userService, imageStorageService,
                postSyncProducer, sensitiveWordChecker, postCacheService, eventPublisher, objectMapper,
                interactionQueryProvider);
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    private void loginAs(Long userId, String role) {
        SecurityContext.set(new SecurityContext.CurrentUser(userId, "author", role));
    }

    private Tag javaTag() {
        Tag tag = new Tag();
        tag.setId(10L);
        tag.setName("Java");
        tag.setSlug("java");
        tag.setPostCount(2);
        return tag;
    }

    private Post ownedPost() {
        Post post = new Post();
        post.setId(POST_ID);
        post.setUserId(AUTHOR_ID);
        post.setTitle("旧标题");
        post.setStatus(1);
        return post;
    }

    private PostTag postTag(Long postId, Long tagId) {
        PostTag rel = new PostTag();
        rel.setPostId(postId);
        rel.setTagId(tagId);
        return rel;
    }

    /** 图片确认原样返回（无临时图片需要迁移） */
    private void stubIdentityImageConfirm() {
        when(imageStorageService.confirmImages(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================== 发布文章 ====================

    @Test
    @DisplayName("发布失败 — 命中敏感词返回 40002 且不落库、不清缓存")
    void createPost_sensitiveWordBlocked_throws() {
        SavePostDTO dto = new SavePostDTO();
        dto.setTitle("正常标题");
        dto.setContent("正文包含违禁词的内容");

        doThrow(new BusinessException(ErrorCode.SENSITIVE_WORD_BLOCKED, "内容包含敏感词：违禁词"))
                .when(sensitiveWordChecker).check(any(String[].class));

        assertThatThrownBy(() -> postService.createPost(AUTHOR_ID, dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(40002));
        verify(postMapper, never()).insert(any(Post.class));
        verifyNoInteractions(postSyncProducer);
        verify(postCacheService, never()).evictHotAll();
    }

    @Test
    @DisplayName("发布成功 — 初始化计数、自动摘要、绑定标签、失效热榜缓存并发送 ES 同步消息")
    void createPost_success() {
        SavePostDTO dto = new SavePostDTO();
        dto.setTitle("我的第一篇文章");
        dto.setContent("# hello");
        dto.setTagIds(List.of(10L));

        stubIdentityImageConfirm();
        when(postMapper.insert(any(Post.class))).thenAnswer(inv -> {
            inv.getArgument(0, Post.class).setId(POST_ID);
            return 1;
        });
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(javaTag()));
        when(postMapper.selectCount(any())).thenReturn(3L);
        when(profileMapper.selectById(AUTHOR_ID)).thenReturn(null);
        when(profileMapper.insert(any(UserProfile.class))).thenReturn(1);

        Long id = postService.createPost(AUTHOR_ID, dto);

        assertThat(id).isEqualTo(POST_ID);
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).insert(captor.capture());
        Post saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(AUTHOR_ID);
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getViewCount()).isZero();
        assertThat(saved.getLikeCount()).isZero();
        assertThat(saved.getCommentCount()).isZero();
        assertThat(saved.getFavoriteCount()).isZero();
        assertThat(saved.getPublishedAt()).isNotNull();
        assertThat(saved.getSummary()).isEqualTo("hello");

        verify(postTagMapper).insert(any(PostTag.class));
        ArgumentCaptor<Tag> tagCaptor = ArgumentCaptor.forClass(Tag.class);
        verify(tagMapper).updateById(tagCaptor.capture());
        assertThat(tagCaptor.getValue().getPostCount()).isEqualTo(3);
        verify(profileMapper).update(any(), any());
        verify(postSyncProducer).send(POST_ID);
        verify(postCacheService).evictHotAll();
        // 无临时图片迁移时不应重复 update
        verify(postMapper, never()).updateById(any(Post.class));

        // 发布事件（驱动 MongoDB 动态流 + 操作日志两份投影）
        ArgumentCaptor<UserEventMessage> eventCaptor =
                ArgumentCaptor.forClass(UserEventMessage.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        UserEventMessage event = eventCaptor.getValue();
        assertThat(event.getAction()).isEqualTo(UserActions.POST_PUBLISH);
        assertThat(event.getUserId()).isEqualTo(AUTHOR_ID);
        assertThat(event.getTargetId()).isEqualTo(POST_ID);
        assertThat(event.getPostTitle()).isEqualTo("我的第一篇文章");
        assertThat(event.getEventId()).isNotBlank();
    }

    // ==================== 编辑文章 ====================

    @Test
    @DisplayName("编辑失败 — 非作者且非管理员拒绝（40301）")
    void updatePost_notOwner_throws() {
        loginAs(AUTHOR_ID, "ROLE_USER");
        Post post = ownedPost();
        post.setUserId(2L);
        when(postMapper.selectById(POST_ID)).thenReturn(post);

        SavePostDTO dto = new SavePostDTO();
        dto.setTitle("新标题");
        dto.setContent("正文");

        assertThatThrownBy(() -> postService.updatePost(AUTHOR_ID, POST_ID, dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.NOT_OWNER.getCode()));
        verify(postMapper, never()).updateById(any(Post.class));
        verifyNoInteractions(sensitiveWordChecker, postSyncProducer);
    }

    @Test
    @DisplayName("编辑成功 — 作者本人可改，旧标签解绑重绑、重新计数并失效详情/热榜缓存")
    void updatePost_success() {
        loginAs(AUTHOR_ID, "ROLE_USER");
        when(postMapper.selectById(POST_ID)).thenReturn(ownedPost());
        stubIdentityImageConfirm();
        when(postTagMapper.selectList(any())).thenReturn(List.of(postTag(POST_ID, 10L)));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(javaTag()));
        when(postTagMapper.delete(any())).thenReturn(1);
        when(postTagMapper.insert(any(PostTag.class))).thenReturn(1);
        when(postMapper.selectCount(any())).thenReturn(4L);

        SavePostDTO dto = new SavePostDTO();
        dto.setTitle("新标题");
        dto.setSummary("新摘要");
        dto.setContent("新正文");
        dto.setCategoryId(3);
        dto.setTagIds(List.of(10L));

        postService.updatePost(AUTHOR_ID, POST_ID, dto);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("新标题");
        assertThat(captor.getValue().getCategoryId()).isEqualTo(3);
        verify(postTagMapper).delete(any());
        verify(postTagMapper).insert(any(PostTag.class));
        ArgumentCaptor<Tag> tagCaptor = ArgumentCaptor.forClass(Tag.class);
        verify(tagMapper).updateById(tagCaptor.capture());
        assertThat(tagCaptor.getValue().getPostCount()).isEqualTo(4);
        verify(postSyncProducer).send(POST_ID);
        verify(postCacheService).evictDetail(POST_ID);
        verify(postCacheService).evictHotAll();
    }

    // ==================== 删除文章 ====================

    @Test
    @DisplayName("删除成功 — 逻辑删除、同步标签与作者计数并失效详情/热榜缓存")
    void deletePost_success() {
        loginAs(AUTHOR_ID, "ROLE_USER");
        when(postMapper.selectById(POST_ID)).thenReturn(ownedPost());
        when(postMapper.deleteById(POST_ID)).thenReturn(1);
        when(postTagMapper.selectList(any())).thenReturn(List.of(postTag(POST_ID, 10L)));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(javaTag()));
        when(postTagMapper.delete(any())).thenReturn(1);
        when(postMapper.selectCount(any())).thenReturn(0L);
        when(profileMapper.update(any(), any())).thenReturn(1);

        postService.deletePost(AUTHOR_ID, POST_ID);

        verify(postMapper).deleteById(POST_ID);
        verify(postTagMapper).delete(any());
        ArgumentCaptor<Tag> tagCaptor = ArgumentCaptor.forClass(Tag.class);
        verify(tagMapper).updateById(tagCaptor.capture());
        assertThat(tagCaptor.getValue().getPostCount()).isZero();
        verify(profileMapper).update(any(), any());
        verify(postSyncProducer).send(POST_ID);
        verify(postCacheService).evictDetail(POST_ID);
        verify(postCacheService).evictHotAll();
    }

    // ==================== 列表缓存 ====================

    private PostQueryDTO hotQuery() {
        PostQueryDTO q = new PostQueryDTO();
        q.setSort("hot");
        return q;
    }

    @Test
    @DisplayName("热榜缓存未命中 — 查库并写入公共副本缓存（个性化字段清零）")
    void list_hotCacheMiss_putsPublicCopy() {
        Post post = new Post();
        post.setId(POST_ID);
        post.setUserId(2L);
        post.setTitle("热榜文章");
        post.setStatus(1);
        post.setViewCount(99);
        Page<Post> page = new Page<>(1, 10);
        page.setRecords(List.of(post));
        page.setTotal(1);

        when(postMapper.selectPage(any(), any())).thenReturn(page);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(hotAuthor()));
        when(postTagMapper.selectList(any())).thenReturn(List.of());
        when(interactionQueryProvider.getIfAvailable()).thenReturn(interactionQuery);
        when(interactionQuery.isLiked(5L, POST_ID)).thenReturn(true);

        PageVO<PostVO> vo = postService.list(hotQuery(), 5L);

        assertThat(vo.getItems()).hasSize(1);
        assertThat(vo.getItems().get(0).getIsLiked()).isTrue();
        verify(userService).isFollowing(5L, 2L);
        ArgumentCaptor<PageVO<PostVO>> captor = ArgumentCaptor.forClass(PageVO.class);
        verify(postCacheService).putHot(eq(1L), eq(10L), captor.capture());
        assertThat(captor.getValue().getItems().get(0).getIsLiked()).isFalse();
        assertThat(captor.getValue().getItems().get(0).getIsFavorited()).isFalse();
        assertThat(captor.getValue().getItems().get(0).getIsFollowingAuthor()).isFalse();
    }

    @Test
    @DisplayName("热榜缓存命中 — 不查库，按登录态补齐个性化字段")
    void list_hotCacheHit_appliesPersonalization() {
        PostVO cachedItem = PostVO.builder()
                .id(POST_ID)
                .title("热榜文章")
                .author(PostVO.Author.builder().id(2L).username("hot-author").build())
                .viewCount(99)
                .build();
        PageVO<PostVO> cached = new PageVO<>();
        cached.setItems(List.of(cachedItem));
        cached.setPage(1);
        cached.setSize(10);
        cached.setTotal(1);
        cached.setTotalPages(1);
        when(postCacheService.getHot(1L, 10L)).thenReturn(cached);
        when(interactionQueryProvider.getIfAvailable()).thenReturn(interactionQuery);
        when(interactionQuery.isLiked(5L, POST_ID)).thenReturn(true);
        when(userService.isFollowing(5L, 2L)).thenReturn(true);

        PageVO<PostVO> vo = postService.list(hotQuery(), 5L);

        assertThat(vo.getItems()).hasSize(1);
        assertThat(vo.getItems().get(0).getIsLiked()).isTrue();
        assertThat(vo.getItems().get(0).getIsFavorited()).isFalse();
        assertThat(vo.getItems().get(0).getIsFollowingAuthor()).isTrue();
        verify(postMapper, never()).selectPage(any(), any());
    }

    // ==================== 详情缓存 ====================

    @Test
    @DisplayName("详情缓存命中 — 不查库且浏览量仍 +1，个性化字段按登录态补齐")
    void detail_cacheHit_skipsDbAndAppliesPersonalization() {
        PostVO cached = PostVO.builder()
                .id(POST_ID)
                .title("缓存文章")
                .author(PostVO.Author.builder().id(2L).username("hot-author").build())
                .build();
        when(postCacheService.getDetail(POST_ID)).thenReturn(cached);
        when(interactionQueryProvider.getIfAvailable()).thenReturn(interactionQuery);
        when(interactionQuery.isLiked(5L, POST_ID)).thenReturn(true);
        when(userService.isFollowing(5L, 2L)).thenReturn(true);

        PostVO vo = postService.detail(POST_ID, 5L);

        assertThat(vo.getIsLiked()).isTrue();
        assertThat(vo.getIsFollowingAuthor()).isTrue();
        // 浏览量 +1 与缓存读取并行不悖
        verify(postMapper).update(any(), any());
        verify(postMapper, never()).selectById(anyLong());
        verify(postCacheService, never()).putDetail(anyLong(), any());
    }

    @Test
    @DisplayName("详情缓存未命中 — 查库组装后写入公共副本缓存（个性化字段清零）")
    void detail_cacheMiss_putsPublicCopy() {
        Post post = new Post();
        post.setId(POST_ID);
        post.setUserId(2L);
        post.setTitle("新文章");
        post.setContent("正文");
        post.setStatus(1);
        post.setViewCount(5);

        when(postMapper.selectById(POST_ID)).thenReturn(post);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(hotAuthor()));
        when(postTagMapper.selectList(any())).thenReturn(List.of());
        when(interactionQueryProvider.getIfAvailable()).thenReturn(interactionQuery);
        when(interactionQuery.isLiked(5L, POST_ID)).thenReturn(true);
        when(interactionQuery.isFavorited(5L, POST_ID)).thenReturn(true);

        PostVO vo = postService.detail(POST_ID, 5L);

        assertThat(vo.getIsLiked()).isTrue();
        assertThat(vo.getIsFavorited()).isTrue();
        ArgumentCaptor<PostVO> captor = ArgumentCaptor.forClass(PostVO.class);
        verify(postCacheService).putDetail(eq(POST_ID), captor.capture());
        assertThat(captor.getValue().getIsLiked()).isFalse();
        assertThat(captor.getValue().getIsFavorited()).isFalse();
        assertThat(captor.getValue().getIsFollowingAuthor()).isFalse();
        assertThat(captor.getValue().getTitle()).isEqualTo("新文章");
    }

    private com.codeknest.module.account.auth.entity.User hotAuthor() {
        com.codeknest.module.account.auth.entity.User user = new com.codeknest.module.account.auth.entity.User();
        user.setId(2L);
        user.setUsername("hot-author");
        return user;
    }

    // ==================== 草稿（MongoDB） ====================

    @Test
    @DisplayName("保存草稿 — 新草稿写入 MongoDB 且不做敏感词校验")
    void saveDraft_newDraft_skipsSensitiveCheck() {
        stubIdentityImageConfirm();
        when(draftMongoRepository.save(any(PostDraftDocument.class))).thenAnswer(inv -> {
            inv.getArgument(0, PostDraftDocument.class).setId("draft-mongo-1");
            return inv.getArgument(0, PostDraftDocument.class);
        });

        SaveDraftDTO dto = new SaveDraftDTO();
        dto.setTitle("草稿标题");
        dto.setContent("草稿正文可以包含任何词");
        dto.setTagNames(List.of("Java"));
        dto.setPostId(POST_ID);

        String id = postService.saveDraft(AUTHOR_ID, dto);

        assertThat(id).isEqualTo("draft-mongo-1");
        ArgumentCaptor<PostDraftDocument> captor = ArgumentCaptor.forClass(PostDraftDocument.class);
        // 新草稿保存两次：先落库拿 id，再确认图片（迁移临时图 + 封面取首图）后二次保存
        verify(draftMongoRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getUserId()).isEqualTo(AUTHOR_ID);
        assertThat(captor.getAllValues().get(0).getTagNames()).isEqualTo("Java");
        assertThat(captor.getAllValues().get(0).getCreatedAt()).isNotNull();
        assertThat(captor.getAllValues().get(1).getUpdatedAt()).isNotNull();
        verifyNoInteractions(sensitiveWordChecker);
        verify(postSyncProducer, never()).send(anyLong());
    }

    @Test
    @DisplayName("保存草稿 — 已有草稿归属校验，非本人拒绝")
    void saveDraft_existing_notOwner_throws() {
        PostDraftDocument existing = new PostDraftDocument();
        existing.setId("draft-mongo-1");
        existing.setUserId(2L);
        when(draftMongoRepository.findById("draft-mongo-1")).thenReturn(Optional.of(existing));

        SaveDraftDTO dto = new SaveDraftDTO();
        dto.setId("draft-mongo-1");
        dto.setTitle("越权改写");

        assertThatThrownBy(() -> postService.saveDraft(AUTHOR_ID, dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.NOT_FOUND.getCode()));
        verify(draftMongoRepository, never()).save(any(PostDraftDocument.class));
    }

    @Test
    @DisplayName("保存草稿 — 已有草稿本人更新内容")
    void saveDraft_existing_updatesContent() {
        PostDraftDocument existing = new PostDraftDocument();
        existing.setId("draft-mongo-1");
        existing.setUserId(AUTHOR_ID);
        existing.setCreatedAt(java.time.LocalDateTime.now().minusDays(1));
        when(draftMongoRepository.findById("draft-mongo-1")).thenReturn(Optional.of(existing));
        stubIdentityImageConfirm();

        SaveDraftDTO dto = new SaveDraftDTO();
        dto.setId("draft-mongo-1");
        dto.setTitle("更新标题");
        dto.setContent("更新正文");

        String id = postService.saveDraft(AUTHOR_ID, dto);

        assertThat(id).isEqualTo("draft-mongo-1");
        ArgumentCaptor<PostDraftDocument> captor = ArgumentCaptor.forClass(PostDraftDocument.class);
        verify(draftMongoRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("更新标题");
        // createdAt 保留首次创建时间
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("删除草稿 — 本人可删")
    void deleteDraft_owned_deletes() {
        PostDraftDocument existing = new PostDraftDocument();
        existing.setId("draft-mongo-1");
        existing.setUserId(AUTHOR_ID);
        when(draftMongoRepository.findById("draft-mongo-1")).thenReturn(Optional.of(existing));

        postService.deleteDraft(AUTHOR_ID, "draft-mongo-1");

        verify(draftMongoRepository).deleteById("draft-mongo-1");
    }

    @Test
    @DisplayName("删除草稿 — 非本人拒绝")
    void deleteDraft_notOwner_throws() {
        PostDraftDocument existing = new PostDraftDocument();
        existing.setId("draft-mongo-1");
        existing.setUserId(2L);
        when(draftMongoRepository.findById("draft-mongo-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> postService.deleteDraft(AUTHOR_ID, "draft-mongo-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.NOT_FOUND.getCode()));
        verify(draftMongoRepository, never()).deleteById(any());
    }
}
