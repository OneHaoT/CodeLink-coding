package com.codeknest.module.account.auth.service.impl;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.account.auth.entity.LoginLogDocument;
import com.codeknest.module.account.auth.repository.LoginLogRepository;
import com.codeknest.module.account.auth.service.LoginLogService;
import com.codeknest.module.account.auth.vo.LoginLogVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 登录日志服务 — MongoDB 存储；写入失败仅记日志，不影响登录主流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogServiceImpl implements LoginLogService {

    private final LoginLogRepository loginLogRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public void record(Long userId, String account, boolean success, String failReason) {
        try {
            LoginLogDocument doc = new LoginLogDocument();
            doc.setUserId(userId);
            doc.setAccount(account);
            doc.setSuccess(success);
            doc.setFailReason(success ? null : failReason);
            doc.setIp(currentIp());
            doc.setUserAgent(currentUserAgent());
            doc.setCreatedAt(LocalDateTime.now());
            loginLogRepository.save(doc);
        } catch (Exception e) {
            log.warn("登录日志记录失败，account={}", account, e);
        }
    }

    @Override
    public PageVO<LoginLogVO> page(int page, int size, String account, Boolean success) {
        int pageNo = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 50);

        Criteria criteria = new Criteria();
        if (StringUtils.hasText(account)) {
            criteria.and("account").regex(Pattern.compile(Pattern.quote(account.trim()), Pattern.CASE_INSENSITIVE));
        }
        if (success != null) {
            criteria.and("success").is(success);
        }

        Query query = Query.query(criteria).with(PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        long total = mongoTemplate.count(Query.query(criteria), LoginLogDocument.class);
        List<LoginLogVO> items = mongoTemplate.find(query, LoginLogDocument.class).stream()
                .map(d -> LoginLogVO.builder()
                        .id(d.getId())
                        .userId(d.getUserId())
                        .account(d.getAccount())
                        .success(d.getSuccess())
                        .failReason(d.getFailReason())
                        .ip(d.getIp())
                        .userAgent(d.getUserAgent())
                        .createdAt(d.getCreatedAt())
                        .build())
                .toList();

        PageVO<LoginLogVO> vo = new PageVO<>();
        vo.setItems(items);
        vo.setPage(pageNo);
        vo.setSize(pageSize);
        vo.setTotal(total);
        vo.setTotalPages((total + pageSize - 1) / pageSize);
        return vo;
    }

    private String currentIp() {
        HttpServletRequest req = currentRequest();
        return req == null ? null : req.getRemoteAddr();
    }

    private String currentUserAgent() {
        HttpServletRequest req = currentRequest();
        return req == null ? null : req.getHeader("User-Agent");
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }
}
