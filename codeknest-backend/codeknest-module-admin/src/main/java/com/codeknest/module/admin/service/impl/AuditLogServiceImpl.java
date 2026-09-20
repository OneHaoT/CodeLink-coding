package com.codeknest.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.admin.dto.AuditLogQueryDTO;
import com.codeknest.module.admin.entity.AuditLog;
import com.codeknest.module.admin.mapper.AuditLogMapper;
import com.codeknest.module.admin.service.AuditLogService;
import com.codeknest.module.admin.support.RequestInfo;
import com.codeknest.module.admin.vo.AuditLogVO;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String target, String detail) {
        try {
            AuditLog row = new AuditLog();
            row.setUserId(SecurityContext.getUserId());
            row.setAction(action);
            row.setTarget(target);
            row.setIp(RequestInfo.currentIp());
            row.setDetail(detail);
            auditLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("写入审计日志失败 action={}, target={}: {}", action, target, e.getMessage());
        }
    }

    @Override
    public PageVO<AuditLogVO> page(AuditLogQueryDTO q) {
        long size = Math.min(q.getSize() == null ? 10 : Math.max(q.getSize(), 1), 50);
        long pageNo = q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage();

        LambdaQueryWrapper<AuditLog> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.getAction())) {
            w.eq(AuditLog::getAction, q.getAction().trim());
        }
        if (q.getUserId() != null) {
            w.eq(AuditLog::getUserId, q.getUserId());
        }
        LocalDate start = q.getStartDate();
        LocalDate end = q.getEndDate();
        if (start != null) {
            w.ge(AuditLog::getCreatedAt, start.atStartOfDay());
        }
        if (end != null) {
            w.le(AuditLog::getCreatedAt, end.atTime(23, 59, 59));
        }
        w.orderByDesc(AuditLog::getCreatedAt).orderByDesc(AuditLog::getId);

        Page<AuditLog> result = auditLogMapper.selectPage(new Page<>(pageNo, size), w);
        Set<Long> userIds = new HashSet<>();
        result.getRecords().forEach(r -> {
            if (r.getUserId() != null) userIds.add(r.getUserId());
        });
        Map<Long, String> nameMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));

        return PageVO.of(result, result.getRecords().stream().map(r -> {
            AuditLogVO vo = new AuditLogVO();
            vo.setId(r.getId());
            vo.setUserId(r.getUserId());
            vo.setOperatorName(r.getUserId() == null ? null
                    : nameMap.getOrDefault(r.getUserId(), "用户#" + r.getUserId()));
            vo.setAction(r.getAction());
            vo.setTarget(r.getTarget());
            vo.setIp(r.getIp());
            vo.setDetail(r.getDetail());
            vo.setCreatedAt(r.getCreatedAt());
            return vo;
        }).toList());
    }
}
