package com.codeknest.module.admin.service;

import com.codeknest.module.content.post.entity.Notice;

import java.util.List;

/**
 * 公告管理的后台服务：包装 {@link com.codeknest.module.content.post.service.NoticeService}，
 * 在写操作后记录审计日志（NoticeService 位于 post 模块，无法引用 admin 侧的 AuditLogService）。
 */
public interface AdminNoticeService {

    List<Notice> listAll();

    Notice create(Notice notice);

    Notice update(Long id, Notice notice);

    void toggleStatus(Long id, Integer status);

    void delete(Long id);
}
