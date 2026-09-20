package com.codeknest.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.common.security.JwtProperties;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.admin.dto.AdminUserQueryDTO;
import com.codeknest.module.admin.service.AdminUserService;
import com.codeknest.module.admin.service.AuditLogService;
import com.codeknest.module.admin.support.AuditActions;
import com.codeknest.module.admin.vo.AdminUserVO;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final StringRedisTemplate redis;
    private final JwtProperties jwtProperties;

    /** 被禁用账号的 access token 黑名单 key（与 JwtAuthenticationFilter 校验侧一致） */
    private static final String ACCESS_TOKEN_BLACKLIST_KEY = "blacklist:access_token:%d";

    @Override
    public PageVO<AdminUserVO> page(AdminUserQueryDTO q) {
        long size = Math.min(q.getSize() == null ? 10 : Math.max(q.getSize(), 1), 50);
        long pageNo = q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage();

        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.getQ())) {
            String kw = q.getQ().trim();
            w.and(x -> x.like(User::getUsername, kw).or().like(User::getEmail, kw));
        }
        if (q.getStatus() != null) {
            w.eq(User::getStatus, q.getStatus());
        }
        w.orderByAsc(User::getId);

        Page<User> result = userMapper.selectPage(new Page<>(pageNo, size), w);
        return PageVO.of(result, result.getRecords().stream().map(u -> {
            AdminUserVO vo = new AdminUserVO();
            vo.setId(u.getId());
            vo.setUsername(u.getUsername());
            vo.setEmail(u.getEmail());
            vo.setRole(u.getRole());
            vo.setStatus(u.getStatus());
            vo.setLastLoginAt(u.getLastLoginAt());
            vo.setCreatedAt(u.getCreatedAt());
            return vo;
        }).toList());
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        User target = requireUser(id);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态仅支持启用(1)或禁用(0)");
        }
        if ("ROLE_ADMIN".equals(target.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "禁止禁用管理员账号");
        }
        if (id.equals(SecurityContext.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能禁用当前登录账号");
        }
        target.setStatus(status);
        userMapper.updateById(target);

        // 同步 access token 黑名单：禁用立即下线（TTL 覆盖最长 token 生命期），启用放行重新登录
        String blacklistKey = String.format(ACCESS_TOKEN_BLACKLIST_KEY, id);
        if (status == 0) {
            redis.opsForValue().set(blacklistKey, "1",
                    jwtProperties.getAccessTokenExpires(), TimeUnit.SECONDS);
        } else {
            redis.delete(blacklistKey);
        }

        auditLogService.record(
                status == 1 ? AuditActions.USER_ENABLE : AuditActions.USER_DISABLE,
                "user:" + id,
                status == 1 ? "启用用户" : "禁用用户");
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        User target = requireUser(id);
        if ("ROLE_ADMIN".equals(target.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "禁止重置管理员账号密码");
        }
        if (id.equals(SecurityContext.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能重置当前登录账号密码");
        }
        target.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(target);

        auditLogService.record(AuditActions.USER_PASSWORD_RESET, "user:" + id, "管理员重置密码");
    }

    // ---------- private ----------

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}
