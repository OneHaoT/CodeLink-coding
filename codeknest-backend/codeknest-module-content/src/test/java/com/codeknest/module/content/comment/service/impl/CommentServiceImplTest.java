package com.codeknest.module.content.comment.service.impl;

import com.codeknest.module.account.event.UserActions;
import com.codeknest.module.account.event.UserEventMessage;
import com.codeknest.module.account.event.UserEventPublisher;
import com.codeknest.module.account.message.service.NotificationService;
import com.codeknest.module.content.comment.dto.CreateCommentDTO;
import com.codeknest.module.content.comment.entity.Comment;
import com.codeknest.module.content.comment.entity.CommentDocument;
import com.codeknest.module.content.comment.entity.CommentLike;
import com.codeknest.module.content.comment.mapper.CommentLikeMapper;
import com.codeknest.module.content.comment.mapper.CommentMapper;
import com.codeknest.module.content.comment.mq.CommentSyncProducer;
import com.codeknest.module.content.comment.repository.CommentMongoRepository;
import com.codeknest.module.content.comment.service.CommentReadCopyService;
import com.codeknest.module.content.comment.vo.CommentVO;
import com.codeknest.module.content.post.entity.Post;
import com.codeknest.module.content.post.mapper.PostMapper;
import com.codeknest.module.content.post.support.SensitiveWordChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 评论服务单元测试 — 重点覆盖「MongoDB 读副本优先 + 未命中/不可用回源 MySQL」的读路径，
 * 以及写路径对副本同步与用户事件的下发。纯 Mockito，不启动 Spring、不连中间件。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    private static final Long POST_ID = 66L;
    private static final Long USER_ID = 9L;

    @Mock
    private CommentMapper commentMapper;
    @Mock
    private CommentLikeMapper likeMapper;
    @Mock
    private PostMapper postMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SensitiveWordChecker sensitiveWordChecker;
    @Mock
    private CommentReadCopyService commentReadCopyService;
    @Mock
    private CommentMongoRepository commentMongoRepository;
    @Mock
    private CommentSyncProducer commentSyncProducer;
    @Mock
    private UserEventPublisher eventPublisher;

    private CommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentServiceImpl(commentMapper, likeMapper, postMapper,
                notificationService, sensitiveWordChecker, commentReadCopyService,
                commentMongoRepository, commentSyncProducer, eventPublisher);
    }

    private CommentDocument doc(String id, Long parentId, String content) {
        CommentDocument d = new CommentDocument();
        d.setId(id);
        d.setPostId(POST_ID);
        d.setParentId(parentId);
        d.setUserId(USER_ID);
        d.setUsername("alice");
        d.setContent(content);
        d.setLikeCount(0);
        d.setLikedUserIds(List.of(USER_ID));
        d.setCreatedAt(LocalDateTime.now());
        return d;
    }

    private Post publishedPost() {
        Post post = new Post();
        post.setId(POST_ID);
        post.setUserId(2L);
        post.setTitle("文章标题");
        post.setStatus(1);
        return post;
    }

    // ==================== 读路径 ====================

    @Test
    @DisplayName("读副本命中 — 直接拼树，不回源 MySQL、不触发重建")
    void listByPost_hitCopy_returnsTreeWithoutFallback() {
        when(commentMongoRepository.findByPostId(POST_ID)).thenReturn(List.of(
                doc("1", null, "顶级评论"),
                doc("2", 1L, "子评论")));

        List<CommentVO> tree = commentService.listByPost(POST_ID, USER_ID);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getContent()).isEqualTo("顶级评论");
        assertThat(tree.get(0).getReplies()).hasSize(1);
        assertThat(tree.get(0).getReplies().get(0).getContent()).isEqualTo("子评论");
        // likedUserIds 命中当前用户
        assertThat(tree.get(0).getIsLiked()).isTrue();
        verifyNoInteractions(commentReadCopyService);
        verify(commentSyncProducer, never()).send(anyLong());
    }

    @Test
    @DisplayName("读副本未命中 — 回源 MySQL 并异步触发副本重建")
    void listByPost_copyMiss_fallsBackAndTriggersRebuild() {
        when(commentMongoRepository.findByPostId(POST_ID)).thenReturn(List.of());
        when(commentReadCopyService.buildFromMysql(POST_ID))
                .thenReturn(List.of(doc("1", null, "回源评论")));

        List<CommentVO> tree = commentService.listByPost(POST_ID, null);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getContent()).isEqualTo("回源评论");
        assertThat(tree.get(0).getIsLiked()).isFalse();
        verify(commentSyncProducer).send(POST_ID);
    }

    @Test
    @DisplayName("读副本不可用 — 降级回源 MySQL，接口不抛异常也不触发重建")
    void listByPost_copyDown_degradesToMysql() {
        when(commentMongoRepository.findByPostId(POST_ID))
                .thenThrow(new RuntimeException("mongo down"));
        when(commentReadCopyService.buildFromMysql(POST_ID))
                .thenReturn(List.of(doc("1", null, "降级评论")));

        List<CommentVO> tree = commentService.listByPost(POST_ID, null);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getContent()).isEqualTo("降级评论");
        verify(commentSyncProducer, never()).send(anyLong());
    }

    @Test
    @DisplayName("无评论 — 回源为空则返回空列表，不触发重建")
    void listByPost_empty_returnsEmpty() {
        when(commentMongoRepository.findByPostId(POST_ID)).thenReturn(List.of());
        when(commentReadCopyService.buildFromMysql(POST_ID)).thenReturn(List.of());

        assertThat(commentService.listByPost(POST_ID, null)).isEmpty();
        verify(commentSyncProducer, never()).send(anyLong());
    }

    @Test
    @DisplayName("父评论缺失 — 子评论兜底挂到根节点，不丢数据")
    void listByPost_orphanReply_fallsBackToRoot() {
        when(commentMongoRepository.findByPostId(POST_ID))
                .thenReturn(List.of(doc("9", 99L, "孤儿回复")));

        List<CommentVO> tree = commentService.listByPost(POST_ID, null);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getContent()).isEqualTo("孤儿回复");
    }

    // ==================== 写路径 ====================

    @Test
    @DisplayName("发表评论 — 通知、副本同步与 COMMENT_CREATE 事件均下发")
    void create_publishesEventAndSyncsCopy() {
        when(postMapper.selectById(POST_ID)).thenReturn(publishedPost());
        when(commentMapper.insert(any(Comment.class))).thenAnswer(inv -> {
            inv.getArgument(0, Comment.class).setId(123L);
            return 1;
        });

        CreateCommentDTO dto = new CreateCommentDTO();
        dto.setPostId(POST_ID);
        dto.setContent("这是一条评论");

        Long id = commentService.create(USER_ID, dto);

        assertThat(id).isEqualTo(123L);
        verify(commentSyncProducer).send(POST_ID);

        ArgumentCaptor<UserEventMessage> captor =
                ArgumentCaptor.forClass(UserEventMessage.class);
        verify(eventPublisher).publish(captor.capture());
        UserEventMessage event = captor.getValue();
        assertThat(event.getAction()).isEqualTo(UserActions.COMMENT_CREATE);
        assertThat(event.getUserId()).isEqualTo(USER_ID);
        assertThat(event.getTargetType()).isEqualTo(UserActions.TARGET_COMMENT);
        assertThat(event.getTargetId()).isEqualTo(123L);
        assertThat(event.getPostId()).isEqualTo(POST_ID);
        assertThat(event.getPostTitle()).isEqualTo("文章标题");
        assertThat(event.getCommentExcerpt()).isEqualTo("这是一条评论");
    }

    @Test
    @DisplayName("删除评论 — 触发副本重建并下发 COMMENT_DELETE 事件")
    void delete_syncsCopyAndPublishesEvent() {
        Comment comment = new Comment();
        comment.setId(123L);
        comment.setPostId(POST_ID);
        comment.setUserId(USER_ID);
        comment.setContent("待删除评论内容");
        when(commentMapper.selectById(123L)).thenReturn(comment);

        commentService.delete(USER_ID, 123L);

        verify(commentMapper).deleteById(123L);
        verify(commentSyncProducer).send(POST_ID);

        ArgumentCaptor<UserEventMessage> captor =
                ArgumentCaptor.forClass(UserEventMessage.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(UserActions.COMMENT_DELETE);
        assertThat(captor.getValue().getCommentExcerpt()).isEqualTo("待删除评论内容");
    }

    @Test
    @DisplayName("点赞评论 — 点赞数 +1 并重建副本")
    void like_syncsCopy() {
        Comment comment = new Comment();
        comment.setId(123L);
        comment.setPostId(POST_ID);
        comment.setUserId(2L);
        comment.setLikeCount(0);
        when(commentMapper.selectById(123L)).thenReturn(comment);
        when(likeMapper.exists(any())).thenReturn(false);

        int count = commentService.like(USER_ID, 123L);

        assertThat(count).isEqualTo(1);
        verify(likeMapper).insert(any(CommentLike.class));
        verify(commentSyncProducer).send(POST_ID);
    }

    @Test
    @DisplayName("点赞幂等 — 已点赞直接返回当前数，不重复写库也不重建副本")
    void like_idempotent() {
        Comment comment = new Comment();
        comment.setId(123L);
        comment.setPostId(POST_ID);
        comment.setLikeCount(5);
        when(commentMapper.selectById(123L)).thenReturn(comment);
        when(likeMapper.exists(any())).thenReturn(true);

        assertThat(commentService.like(USER_ID, 123L)).isEqualTo(5);
        verify(likeMapper, never()).insert(any(CommentLike.class));
        verify(commentSyncProducer, never()).send(anyLong());
    }
}