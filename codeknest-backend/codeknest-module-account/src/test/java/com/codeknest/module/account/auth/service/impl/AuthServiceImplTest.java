package com.codeknest.module.account.auth.service.impl;

import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.common.security.JwtProperties;
import com.codeknest.common.security.JwtUtils;
import com.codeknest.module.account.auth.dto.LoginDTO;
import com.codeknest.module.account.auth.dto.PasswordChangeDTO;
import com.codeknest.module.account.auth.dto.RegisterDTO;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.account.auth.vo.LoginVO;
import com.codeknest.module.account.auth.vo.RegisterVO;
import com.codeknest.module.account.event.UserActions;
import com.codeknest.module.account.event.UserEventMessage;
import com.codeknest.module.account.event.UserEventPublisher;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试 — 纯 Mockito，不启动 Spring、不连中间件
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private UserEventPublisher eventPublisher;

    private JwtProperties jwtProperties;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        authService = new AuthServiceImpl(userMapper, passwordEncoder, jwtUtils, jwtProperties,
                redis, eventPublisher);
    }

    /** 抓取本次发布的事件（登录日志已泛化为统一用户事件） */
    private UserEventMessage captureEvent() {
        ArgumentCaptor<UserEventMessage> captor = ArgumentCaptor.forClass(UserEventMessage.class);
        verify(eventPublisher).publish(captor.capture());
        return captor.getValue();
    }

    private User activeUser() {
        User user = new User();
        user.setId(5L);
        user.setUsername("admin");
        user.setEmail("admin@codeknest.dev");
        user.setPassword("$2a$10$stored-hash");
        user.setStatus(1);
        user.setRole("ROLE_USER");
        return user;
    }

    // ==================== 注册 ====================

    @Test
    @DisplayName("注册成功 — 返回用户信息且密码已加密")
    void register_success() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setPassword("raw123456");

        when(userMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("raw123456")).thenReturn("encoded-hash");
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            inv.getArgument(0, User.class).setId(100L);
            return 1;
        });

        RegisterVO vo = authService.register(dto);

        assertThat(vo.getUserId()).isEqualTo(100L);
        assertThat(vo.getUsername()).isEqualTo("newuser");
        assertThat(vo.getEmail()).isNull();
        verify(userMapper).insert(any(User.class));

        UserEventMessage event = captureEvent();
        assertThat(event.getAction()).isEqualTo(UserActions.REGISTER);
        assertThat(event.getUserId()).isEqualTo(100L);
        assertThat(event.getAccount()).isEqualTo("newuser");
        assertThat(event.getTargetType()).isEqualTo(UserActions.TARGET_USER);
        assertThat(event.getEventId()).isNotBlank();
    }

    @Test
    @DisplayName("注册失败 — 昵称已被占用")
    void register_duplicateUsername_throws() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("admin");
        dto.setPassword("raw123456");

        when(userMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> authService.register(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.USERNAME_EXISTS.getCode()));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("注册失败 — 邮箱已被注册")
    void register_duplicateEmail_throws() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setEmail("taken@codeknest.dev");
        dto.setPassword("raw123456");

        when(userMapper.selectCount(any())).thenReturn(0L, 1L);

        assertThatThrownBy(() -> authService.register(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.EMAIL_EXISTS.getCode()));
        verify(userMapper, never()).insert(any(User.class));
    }

    // ==================== 登录 ====================

    @Test
    @DisplayName("登录成功 — 签发双 Token 并更新最后登录时间")
    void login_success() {
        LoginDTO dto = new LoginDTO();
        dto.setAccount("admin");
        dto.setPassword("raw123");
        User user = activeUser();

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("raw123", "$2a$10$stored-hash")).thenReturn(true);
        when(jwtUtils.generateAccessToken(5L, "admin", "ROLE_USER")).thenReturn("access-jwt");
        when(jwtUtils.generateRefreshToken(5L, "admin")).thenReturn("refresh-jwt");

        LoginVO vo = authService.login(dto);

        assertThat(vo.getAccessToken()).isEqualTo("access-jwt");
        assertThat(vo.getRefreshToken()).isEqualTo("refresh-jwt");
        assertThat(vo.getTokenType()).isEqualTo("Bearer");
        assertThat(vo.getExpiresIn()).isEqualTo(jwtProperties.getAccessTokenExpires());
        assertThat(vo.getUser().getId()).isEqualTo(5L);
        assertThat(vo.getUser().getRole()).isEqualTo("ROLE_USER");
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(userMapper).updateById(user);

        UserEventMessage event = captureEvent();
        assertThat(event.getAction()).isEqualTo(UserActions.LOGIN_SUCCESS);
        assertThat(event.getUserId()).isEqualTo(5L);
        assertThat(event.getAccount()).isEqualTo("admin");
        assertThat(event.getSuccess()).isTrue();
        assertThat(event.getFailReason()).isNull();
    }

    @Test
    @DisplayName("登录失败 — 密码错误")
    void login_wrongPassword_throws() {
        LoginDTO dto = new LoginDTO();
        dto.setAccount("admin");
        dto.setPassword("bad");
        User user = activeUser();

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("bad", "$2a$10$stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.LOGIN_FAIL.getCode()));
        verify(userMapper, never()).updateById(any(User.class));

        UserEventMessage event = captureEvent();
        assertThat(event.getAction()).isEqualTo(UserActions.LOGIN_FAIL);
        assertThat(event.getUserId()).isEqualTo(5L);
        assertThat(event.getSuccess()).isFalse();
        assertThat(event.getFailReason()).isEqualTo("密码错误");
    }

    @Test
    @DisplayName("登录失败 — 账号不存在")
    void login_userNotFound_throws() {
        LoginDTO dto = new LoginDTO();
        dto.setAccount("ghost");
        dto.setPassword("raw123");

        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.LOGIN_FAIL.getCode()));
        verify(jwtUtils, never()).generateAccessToken(anyLong(), anyString(), anyString());

        UserEventMessage event = captureEvent();
        assertThat(event.getAction()).isEqualTo(UserActions.LOGIN_FAIL);
        assertThat(event.getUserId()).isNull();
        assertThat(event.getAccount()).isEqualTo("ghost");
        assertThat(event.getFailReason()).isEqualTo("账号不存在");
    }

    // ==================== 刷新 Token ====================

    @Test
    @DisplayName("刷新成功 — 校验 refresh 类型并换发双 Token")
    void refresh_success() {
        Claims claims = mock(Claims.class);
        User user = activeUser();

        when(jwtUtils.parseToken("refresh-jwt")).thenReturn(claims);
        when(jwtUtils.getType(claims)).thenReturn("refresh");
        when(jwtUtils.getUserId(claims)).thenReturn(5L);
        when(userMapper.selectById(5L)).thenReturn(user);
        when(jwtUtils.generateAccessToken(5L, "admin", "ROLE_USER")).thenReturn("new-access");
        when(jwtUtils.generateRefreshToken(5L, "admin")).thenReturn("new-refresh");

        LoginVO vo = authService.refresh("refresh-jwt");

        assertThat(vo.getAccessToken()).isEqualTo("new-access");
        assertThat(vo.getRefreshToken()).isEqualTo("new-refresh");
        assertThat(vo.getUser().getUsername()).isEqualTo("admin");
    }

    @Test
    @DisplayName("刷新失败 — Token 无效（含黑名单/过期被拒场景）")
    void refresh_invalidToken_throws() {
        when(jwtUtils.parseToken("broken-token")).thenReturn(null);

        assertThatThrownBy(() -> authService.refresh("broken-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.TOKEN_INVALID.getCode()));
        verify(userMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("刷新失败 — access 类型 Token 不能用于刷新")
    void refresh_accessTypeRejected_throws() {
        Claims claims = mock(Claims.class);

        when(jwtUtils.parseToken("access-jwt")).thenReturn(claims);
        when(jwtUtils.getType(claims)).thenReturn("access");

        assertThatThrownBy(() -> authService.refresh("access-jwt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.TOKEN_INVALID.getCode()));
    }

    @Test
    @DisplayName("刷新失败 — 用户在黑名单中（已登出/改密）拒绝刷新")
    void refresh_blacklisted_throws() {
        Claims claims = mock(Claims.class);

        when(jwtUtils.parseToken("refresh-jwt")).thenReturn(claims);
        when(jwtUtils.getType(claims)).thenReturn("refresh");
        when(jwtUtils.getUserId(claims)).thenReturn(5L);
        when(redis.hasKey("blacklist:refresh_token:5")).thenReturn(true);

        assertThatThrownBy(() -> authService.refresh("refresh-jwt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.TOKEN_INVALID.getCode()));
        verify(userMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("刷新成功 — 黑名单未命中放行并显式查询黑名单")
    void refresh_notBlacklisted_passes() {
        Claims claims = mock(Claims.class);
        User user = activeUser();

        when(jwtUtils.parseToken("refresh-jwt")).thenReturn(claims);
        when(jwtUtils.getType(claims)).thenReturn("refresh");
        when(jwtUtils.getUserId(claims)).thenReturn(5L);
        when(redis.hasKey("blacklist:refresh_token:5")).thenReturn(false);
        when(userMapper.selectById(5L)).thenReturn(user);
        when(jwtUtils.generateAccessToken(5L, "admin", "ROLE_USER")).thenReturn("new-access");
        when(jwtUtils.generateRefreshToken(5L, "admin")).thenReturn("new-refresh");

        LoginVO vo = authService.refresh("refresh-jwt");

        assertThat(vo.getAccessToken()).isEqualTo("new-access");
        verify(redis).hasKey("blacklist:refresh_token:5");
    }

    // ==================== 登出 ====================

    @Test
    @DisplayName("登出 — 将 refreshToken 按 userId 写入黑名单并带 TTL")
    void logout_writesBlacklist() {
        when(redis.opsForValue()).thenReturn(valueOps);

        authService.logout(5L);

        verify(valueOps).set(
                eq("blacklist:refresh_token:5"),
                eq("1"),
                eq(jwtProperties.getRefreshTokenExpires()),
                eq(TimeUnit.SECONDS)
        );
    }

    // ==================== 修改密码 ====================

    @Test
    @DisplayName("修改密码失败 — 原密码错误")
    void changePassword_wrongOld_throws() {
        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setOldPassword("wrong-old");
        dto.setNewPassword("new-password-1");
        User user = activeUser();

        when(userMapper.selectById(5L)).thenReturn(user);
        when(passwordEncoder.matches("wrong-old", "$2a$10$stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(5L, dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.PASSWORD_ERROR.getCode()));
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("修改密码成功 — 更新密码并登出拉黑旧 Token")
    void changePassword_success() {
        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setOldPassword("old-password");
        dto.setNewPassword("new-password-1");
        User user = activeUser();

        when(redis.opsForValue()).thenReturn(valueOps);
        when(userMapper.selectById(5L)).thenReturn(user);
        when(passwordEncoder.matches("old-password", "$2a$10$stored-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password-1")).thenReturn("new-hash");

        authService.changePassword(5L, dto);

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(userMapper).updateById(user);
        verify(valueOps).set(eq("blacklist:refresh_token:5"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
    }
}
