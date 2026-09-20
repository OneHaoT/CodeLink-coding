package com.codeknest.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.admin.service.AuditLogService;
import com.codeknest.module.admin.service.SensitiveWordService;
import com.codeknest.module.admin.support.AuditActions;
import com.codeknest.module.content.post.entity.SensitiveWord;
import com.codeknest.module.content.post.mapper.SensitiveWordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {

    private final SensitiveWordMapper wordMapper;
    private final AuditLogService auditLogService;

    @Override
    public PageVO<SensitiveWord> page(String word, String category, Long pageNo, Long sizeReq) {
        long size = Math.min(sizeReq == null ? 10 : Math.max(sizeReq, 1), 50);
        long pageN = pageNo == null || pageNo < 1 ? 1 : pageNo;

        LambdaQueryWrapper<SensitiveWord> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(word)) {
            w.like(SensitiveWord::getWord, word.trim());
        }
        if (StringUtils.hasText(category)) {
            w.eq(SensitiveWord::getCategory, category.trim());
        }
        w.orderByDesc(SensitiveWord::getId);
        return PageVO.of(wordMapper.selectPage(new Page<>(pageN, size), w),
                java.util.function.Function.identity());
    }

    @Override
    public SensitiveWord create(SensitiveWord req) {
        String word = requireWord(req.getWord());
        ensureWordNotExists(word, null);
        SensitiveWord row = new SensitiveWord();
        row.setWord(word);
        row.setCategory(normalizeCategory(req.getCategory()));
        wordMapper.insert(row);
        auditLogService.record(AuditActions.SENSITIVE_CREATE, "sensitive-word:" + row.getId(), word);
        return row;
    }

    @Override
    public void update(Long id, SensitiveWord req) {
        SensitiveWord row = wordMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "敏感词不存在");
        }
        String word = requireWord(req.getWord());
        ensureWordNotExists(word, id);
        row.setWord(word);
        row.setCategory(normalizeCategory(req.getCategory()));
        wordMapper.updateById(row);
        auditLogService.record(AuditActions.SENSITIVE_UPDATE, "sensitive-word:" + id, word);
    }

    @Override
    public void delete(Long id) {
        SensitiveWord row = wordMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "敏感词不存在");
        }
        wordMapper.deleteById(id);
        auditLogService.record(AuditActions.SENSITIVE_DELETE, "sensitive-word:" + id, row.getWord());
    }

    // ---------- private ----------

    private String requireWord(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "敏感词不能为空");
        }
        String word = raw.trim();
        if (word.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "敏感词长度不能超过 64");
        }
        return word;
    }

    private String normalizeCategory(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String c = raw.trim();
        return c.length() > 32 ? c.substring(0, 32) : c;
    }

    private void ensureWordNotExists(String word, Long excludeId) {
        LambdaQueryWrapper<SensitiveWord> w = new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getWord, word);
        if (excludeId != null) {
            w.ne(SensitiveWord::getId, excludeId);
        }
        if (wordMapper.selectCount(w) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "敏感词已存在");
        }
    }
}
