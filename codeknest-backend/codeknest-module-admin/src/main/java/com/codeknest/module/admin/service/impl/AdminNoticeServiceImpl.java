package com.codeknest.module.admin.service.impl;

import com.codeknest.module.admin.service.AdminNoticeService;
import com.codeknest.module.admin.service.AuditLogService;
import com.codeknest.module.admin.support.AuditActions;
import com.codeknest.module.content.post.entity.Notice;
import com.codeknest.module.content.post.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminNoticeServiceImpl implements AdminNoticeService {

    private final NoticeService noticeService;
    private final AuditLogService auditLogService;

    @Override
    public List<Notice> listAll() {
        return noticeService.listAll();
    }

    @Override
    public Notice create(Notice notice) {
        Notice created = noticeService.create(notice);
        auditLogService.record(AuditActions.NOTICE_CREATE, "notice:" + created.getId(), created.getTitle());
        return created;
    }

    @Override
    public Notice update(Long id, Notice notice) {
        Notice updated = noticeService.update(id, notice);
        auditLogService.record(AuditActions.NOTICE_UPDATE, "notice:" + id, updated.getTitle());
        return updated;
    }

    @Override
    public void toggleStatus(Long id, Integer status) {
        noticeService.toggleStatus(id, status);
        auditLogService.record(AuditActions.NOTICE_STATUS, "notice:" + id,
                status != null && status == 1 ? "上架" : "下架");
    }

    @Override
    public void delete(Long id) {
        noticeService.delete(id);
        auditLogService.record(AuditActions.NOTICE_DELETE, "notice:" + id, "删除公告");
    }
}
