package com.codeknest.module.account.activity.service.impl;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.account.activity.entity.UserActivityDocument;
import com.codeknest.module.account.activity.repository.UserActivityRepository;
import com.codeknest.module.account.activity.vo.UserActivityVO;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.account.event.UserActions;
import com.codeknest.module.account.event.UserEventMessage;
import com.codeknest.module.account.user.entity.UserFollow;
import com.codeknest.module.account.user.mapper.UserFollowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户动态服务单元测试 — 覆盖写入投影（补发起人昵称/头像）、个人动态与关注流、MongoDB 不可用降级
 */
@ExtendWith(MockitoExtension.class)
class UserActivityServiceImplTest {

    private static final Long USER_ID = 5L;

    @Mock
    private UserActivityRepository repository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserFollowMapper followMapper;

    private UserActivityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserActivityServiceImpl(repository, mongoTemplate, userMapper, followMapper);
    }

    private User actor() {
        User u = new User();
        u.setId(USER_ID);
        u.setUsername("alice");
        u.setAvatar("/api/files/avatars/a.png");
        return u;
    }

    private UserActivityDocument doc(String id, Long userId) {
        UserActivityDocument d = new UserActivityDocument();
        d.setId(id);
        d.setUserId(userId);
        d.setActorUsername("alice");
        d.setAction(UserActions.POST_PUBLISH);
        d.setPostId(66L);
        d.setPostTitle("文章标题");
        d.setCreatedAt(LocalDateTime.now());
        return d;
    }

    @Test
    @DisplayName("写入动态 — _id 取 eventId，并补齐发起人昵称与头像")
    void save_fillsActorAndUsesEventId() {
        when(userMapper.selectById(USER_ID)).thenReturn(actor());
        UserEventMessage m = UserEventMessage.of(UserActions.POST_PUBLISH, USER_ID, "alice")
                .postSnapshot(66L, "文章标题", "摘要", "/api/files/posts/1/a.png");

        service.save(m);

        ArgumentCaptor<UserActivityDocument> captor =
                ArgumentCaptor.forClass(UserActivityDocument.class);
        verify(repository).save(captor.capture());
        UserActivityDocument saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(m.getEventId());
        assertThat(saved.getActorUsername()).isEqualTo("alice");
        assertThat(saved.getActorAvatar()).isEqualTo("/api/files/avatars/a.png");
        assertThat(saved.getPostTitle()).isEqualTo("文章标题");
        assertThat(saved.getCreatedAt()).isEqualTo(m.getOccurredAt());
    }

    @Test
    @DisplayName("写入失败 — MongoDB 异常被吞掉，不影响业务主流程")
    void save_repositoryDown_doesNotThrow() {
        when(userMapper.selectById(USER_ID)).thenReturn(actor());
        when(repository.save(any(UserActivityDocument.class)))
                .thenThrow(new RuntimeException("mongo down"));

        service.save(UserEventMessage.of(UserActions.FOLLOW, USER_ID, "alice"));
        // 不抛异常即通过
    }

    @Test
    @DisplayName("我的动态 — 按 userId 分页返回投影 VO")
    void pageByUser_returnsMappedVos() {
        when(mongoTemplate.count(any(Query.class), eq(UserActivityDocument.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(UserActivityDocument.class)))
                .thenReturn(List.of(doc("evt-1", USER_ID)));

        PageVO<UserActivityVO> page = service.pageByUser(USER_ID, 1, 10);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).getPostTitle()).isEqualTo("文章标题");
    }

    @Test
    @DisplayName("关注流 — 关注关系实时取自 MySQL（含自己）")
    void pageFollowing_includesSelfAndFollowedUsers() {
        UserFollow rel = new UserFollow();
        rel.setFollowerId(USER_ID);
        rel.setFollowingId(8L);
        when(followMapper.selectList(any())).thenReturn(List.of(rel));
        when(mongoTemplate.count(any(Query.class), eq(UserActivityDocument.class))).thenReturn(2L);
        when(mongoTemplate.find(any(Query.class), eq(UserActivityDocument.class)))
                .thenReturn(List.of(doc("evt-1", USER_ID), doc("evt-2", 8L)));

        PageVO<UserActivityVO> page = service.pageFollowing(USER_ID, 1, 10);

        assertThat(page.getItems()).hasSize(2);
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(UserActivityDocument.class));
        String json = queryCaptor.getValue().getQueryObject().toJson();
        assertThat(json).contains("5").contains("8");
    }

    @Test
    @DisplayName("查询失败 — 降级为空列表，接口不抛异常")
    void query_mongoDown_returnsEmptyPage() {
        when(followMapper.selectList(any())).thenReturn(List.of());
        when(mongoTemplate.count(any(Query.class), eq(UserActivityDocument.class)))
                .thenThrow(new RuntimeException("mongo down"));

        PageVO<UserActivityVO> page = service.pageFollowing(USER_ID, 1, 10);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getTotal()).isZero();
    }

    @Test
    @DisplayName("未登录 — 关注流返回空列表，不查库")
    void pageFollowing_anonymous_returnsEmpty() {
        PageVO<UserActivityVO> page = service.pageFollowing(null, 1, 10);

        assertThat(page.getItems()).isEmpty();
    }
}