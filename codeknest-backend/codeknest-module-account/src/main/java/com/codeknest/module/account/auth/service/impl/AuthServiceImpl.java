package com.codeknest.module.account.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.common.security.JwtProperties;
import com.codeknest.common.security.JwtUtils;
import com.codeknest.module.account.auth.dto.LoginDTO;
import com.codeknest.module.account.auth.dto.PasswordChangeDTO;
import com.codeknest.module.account.auth.dto.RegisterDTO;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.account.auth.service.AuthService;
import com.codeknest.module.account.auth.service.LoginLogService;
import com.codeknest.module.account.auth.vo.LoginVO;
import com.codeknest.module.account.auth.vo.RegisterVO;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redis;
    private final LoginLogService loginLogService;

    private static final String REFRESH_TOKEN_BLACKLIST_KEY = "blacklist:refresh_token:%d";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterVO register(RegisterDTO dto) {
        // 1. 昵称唯一
        long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername())
        );
        if (usernameCount > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        // 2. 邮箱可选；传了才校验唯一
        String email = dto.getEmail() == null || dto.getEmail().isBlank() ? null : dto.getEmail().trim();
        if (email != null) {
            long emailCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, email)
            );
            if (emailCount > 0) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            }
        }
        // 3. 创建用户
        User user = new User();
        user.setEmail(email);
        user.setUsername(dto.getUsername().trim());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(1);
        user.setRole("ROLE_USER");
        userMapper.insert(user);

        log.info("User registered: id={}, username={}", user.getId(), user.getUsername());
        return RegisterVO.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .build();
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        String account = dto.getAccount().trim();
        // 1. 按邮箱或昵称查用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account)
                .or()
                .eq(User::getEmail, account)
        );
        if (user == null) {
            loginLogService.record(null, account, false, "账号不存在");
            throw new BusinessException(ErrorCode.LOGIN_FAIL);
        }
        // 2. 校验密码
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            loginLogService.record(user.getId(), account, false, "密码错误");
            throw new BusinessException(ErrorCode.LOGIN_FAIL);
        }
        // 3. 检查状态
        if (user.getStatus() != null && user.getStatus() != 1) {
            loginLogService.record(user.getId(), account, false, "账号已被禁用");
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        // 4. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        loginLogService.record(user.getId(), account, true, null);
        // 5. 签发 Token
        return buildLoginResponse(user);
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        Claims claims = jwtUtils.parseToken(refreshToken);
        if (claims == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        if (!"refresh".equals(jwtUtils.getType(claims))) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Long userId = jwtUtils.getUserId(claims);
        // 已登出/改密的用户在黑名单中，拒绝刷新（与 logout 写入侧 key 一致）
        Boolean blacklisted = redis.hasKey(String.format(REFRESH_TOKEN_BLACKLIST_KEY, userId));
        if (Boolean.TRUE.equals(blacklisted)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return buildLoginResponse(user);
    }

    @Override
    public void logout(Long userId) {
        // 将当前用户的 refreshToken 加入黑名单（V1 按 userId 拉黑）
        redis.opsForValue().set(
                String.format(REFRESH_TOKEN_BLACKLIST_KEY, userId),
                "1",
                jwtProperties.getRefreshTokenExpires(),
                TimeUnit.SECONDS
        );
    }

    @Override
    public void changePassword(Long userId, PasswordChangeDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(user);
        logout(userId);
    }

    private LoginVO buildLoginResponse(User user) {
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId(), user.getUsername());

        LoginVO.UserInfo info = LoginVO.UserInfo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .build();

        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpires())
                .user(info)
                .build();
    }
}
