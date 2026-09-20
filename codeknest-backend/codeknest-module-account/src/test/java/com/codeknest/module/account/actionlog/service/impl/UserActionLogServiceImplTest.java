package com.codeknest.module.account.actionlog.service.impl;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.account.actionlog.entity.UserActionLogDocument;
import com.codeknest.module.account.actionlog.repository.UserActionLogRepository;
import com.codeknest.module.account.actionlog.vo.UserActionLogVO;
import com.codeknest.module.account.event.UserActions;
import com.codeknest.module.account.event.UserEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通用用户操作日志服务单元测试 — 覆盖写入投影、动作描述与分页查询
 */
@ExtendWith(MockitoExtension.class)
class UserActionLogServiceImplTest {

    @Mock
    private UserActionLogRepository repository;
    @Mock
    private MongoTemplate mongoTemplate;

    private UserActionLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserActionLogServiceImpl(repository, mongoTemplate);
    }

    private UserEventMessage loginEvent(boolean success, String failReason) {
        UserEventMessage m = UserEventMessage.of(
                success ? UserActions.LOGIN_SUCCESS : UserActions.LOGIN_FAIL, 5L, "admin");
        return m.result(success, failReason);
    }

    @Test
    @DisplayName("写入日志 — _id 取 eventId（幂等）并映射成败与请求信息")
    void save_usesEventIdAsId() {
        UserEventMessage m = loginEvent(false, "密码错误");
        m.setIp("10.0.0.1");
        m.setUserAgent("JUnit");

        service.save(m);

        ArgumentCaptor<UserActionLogDocument> captor =
                ArgumentCaptor.forClass(UserActionLogDocument.class);
        verify(repository).save(captor.capture());
        UserActionLogDocument doc = captor.getValue();
        assertThat(doc.getId()).isEqualTo(m.getEventId());
        assertThat(doc.getAction()).isEqualTo(UserActions.LOGIN_FAIL);
        assertThat(doc.getAccount()).isEqualTo("admin");
        assertThat(doc.getSuccess()).isFalse();
        assertThat(doc.getDetail()).isEqualTo("登录失败：密码错误");
        assertThat(doc.getIp()).isEqualTo("10.0.0.1");
        assertThat(doc.getUserAgent()).isEqualTo("JUnit");
        assertThat(doc.getCreatedAt()).isEqualTo(m.getOccurredAt());
    }

    @Test
    @DisplayName("写入日志 — 动作描述可读（发文/评论/关注）")
    void save_buildsReadableDetail() {
        UserEventMessage m = UserEventMessage.of(UserActions.COMMENT_CREATE, 5L, "admin")
                .postSnapshot(66L, "文章标题", "摘要", null)
                .commentExcerpt("写得不错");

        service.save(m);

        ArgumentCaptor<UserActionLogDocument> captor =
                ArgumentCaptor.forClass(UserActionLogDocument.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isEqualTo("评论了文章《文章标题》：写得不错");
    }

    @Test
    @DisplayName("写入失败 — MongoDB 异常被吞掉，不影响业务主流程")
    void save_repositoryDown_doesNotThrow() {
        when(repository.save(any(UserActionLogDocument.class)))
                .thenThrow(new RuntimeException("mongo down"));

        service.save(loginEvent(true, null));
        // 不抛异常即通过
    }

    @Test
    @DisplayName("eventId 为空 — 直接跳过")
    void save_withoutEventId_skipped() {
        service.save(new UserEventMessage());
        service.save(null);
    }

    @Test
    @DisplayName("分页查询 — 返回投影后的 VO 列表与总数")
    void page_returnsMappedVos() {
        UserActionLogDocument doc = new UserActionLogDocument();
        doc.setId("evt-1");
        doc.setUserId(5L);
        doc.setAccount("admin");
        doc.setAction(UserActions.POST_PUBLISH);
        doc.setDetail("发表了文章《x》");
        doc.setCreatedAt(LocalDateTime.now());

        when(mongoTemplate.count(any(Query.class), eq(UserActionLogDocument.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(UserActionLogDocument.class)))
                .thenReturn(List.of(doc));

        PageVO<UserActionLogVO> page = service.page(1, 10, UserActions.POST_PUBLISH, "adm",
                5L, LocalDate.now().minusDays(1), LocalDate.now());

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).getAction()).isEqualTo(UserActions.POST_PUBLISH);
    }
}