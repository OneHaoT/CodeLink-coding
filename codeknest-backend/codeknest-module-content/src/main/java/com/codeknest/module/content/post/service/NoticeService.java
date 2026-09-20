package com.codeknest.module.content.post.service;

import com.codeknest.module.content.post.entity.Notice;

import java.util.List;

public interface NoticeService {

    /** 前台：已发布公告，按 sort_order 倒序、id 倒序 */
    List<Notice> listPublished();

    /** 后台：全量列表（含下架），按 sort_order 倒序 */
    List<Notice> listAll();

    Notice create(Notice notice);

    Notice update(Long id, Notice notice);

    void delete(Long id);

    /** 上下架：status 1/0 */
    void toggleStatus(Long id, Integer status);
}
