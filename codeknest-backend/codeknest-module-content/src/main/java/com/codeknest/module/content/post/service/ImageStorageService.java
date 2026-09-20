package com.codeknest.module.content.post.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 图片存储服务。
 * 当前实现为本地存储（{@link com.codeknest.module.content.post.service.impl.LocalImageStorageServiceImpl}），
 * 预留阿里云 OSS 升级空间——未来新增 OssImageStorageServiceImpl 并切换 Bean 即可。
 *
 * 生命周期：
 * 1. 编辑时上传 → 进入临时目录（tmp），返回临时 URL；
 * 2. 文章发布 / 草稿保存 → 将正文中引用的临时图片确认为正式图片（迁移到 posts/{postId} 目录），
 *    并把正文中的临时 URL 改写为正式 URL；
 * 3. 未被引用的临时图片由定时任务清理，避免脏数据堆积。
 */
public interface ImageStorageService {

    /**
     * 上传图片到临时目录，返回临时访问 URL。
     */
    String uploadTemp(MultipartFile file, Long userId);

    /**
     * 将正文中引用的临时图片确认为正式图片，返回替换 URL 后的正文。
     *
     * @param content 文章正文（Markdown / HTML）
     * @param refId   引用 ID（文章或草稿 ID，仅作非空校验，不参与目录归档——目录按日期归档）
     * @return 替换了图片 URL 的正文
     */
    String confirmImages(String content, String refId);

    /**
     * 确认单张临时图片（用于封面图等非正文场景），返回正式 URL；若非临时图片则原样返回。
     */
    String confirmImage(String url, String refId);

    /**
     * 删除指定的临时图片（异步，通过 MQ 消费端执行实际删除，不阻塞请求线程）。
     * 非临时 URL 或文件不存在则忽略。
     */
    void deleteTemp(List<String> urls);

    /**
     * 同步执行实际的临时图片文件删除（供 MQ 消费者调用）。
     */
    void doDeleteTemp(List<String> urls);

    /**
     * 清理超过指定小时数的临时图片。
     */
    void cleanupTemp(int olderThanHours);
}
