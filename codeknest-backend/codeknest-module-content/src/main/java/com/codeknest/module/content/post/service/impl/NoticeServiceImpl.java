package com.codeknest.module.content.post.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.module.content.post.entity.Notice;
import com.codeknest.module.content.post.mapper.NoticeMapper;
import com.codeknest.module.content.post.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private final NoticeMapper noticeMapper;

    @Override
    public List<Notice> listPublished() {
        return noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, 1)
                .orderByDesc(Notice::getSortOrder)
                .orderByDesc(Notice::getId));
    }

    @Override
    public List<Notice> listAll() {
        return noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .orderByDesc(Notice::getSortOrder)
                .orderByDesc(Notice::getId));
    }

    @Override
    public Notice create(Notice notice) {
        validate(notice);
        if (notice.getStatus() == null) notice.setStatus(1);
        if (notice.getSortOrder() == null) notice.setSortOrder(0);
        noticeMapper.insert(notice);
        return notice;
    }

    @Override
    public Notice update(Long id, Notice notice) {
        Notice existing = requireNotice(id);
        validate(notice);
        existing.setTitle(notice.getTitle());
        existing.setContent(notice.getContent());
        if (notice.getStatus() != null) existing.setStatus(notice.getStatus());
        if (notice.getSortOrder() != null) existing.setSortOrder(notice.getSortOrder());
        noticeMapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        requireNotice(id);
        noticeMapper.deleteById(id);
    }

    @Override
    public void toggleStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态仅支持 1=发布 0=下架");
        }
        Notice existing = requireNotice(id);
        existing.setStatus(status);
        noticeMapper.updateById(existing);
    }

    private Notice requireNotice(Long id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        return notice;
    }

    private void validate(Notice notice) {
        if (!StringUtils.hasText(notice.getTitle())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标题不能为空");
        }
        if (!StringUtils.hasText(notice.getContent())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容不能为空");
        }
    }
}
