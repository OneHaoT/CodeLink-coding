package com.codeknest.module.content.post.service.impl;

import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.module.content.post.mq.ImageDeleteProducer;
import com.codeknest.module.content.post.service.ImageStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地图片存储实现。
 * 目录结构：
 *   {imagePath}/tmp/{yyyy-M-d}/{uuid}.{ext}        — 编辑中临时图片（按日期归档）
 *   {imagePath}/posts/{yyyy-M-d}/{uuid}.{ext}      — 已确认的正式图片（按日期归档）
 * 访问 URL：
 *   /api/image/tmp/{yyyy-M-d}/{uuid}.{ext}
 *   /api/image/posts/{yyyy-M-d}/{uuid}.{ext}
 */
@Slf4j
@Service
public class LocalImageStorageServiceImpl implements ImageStorageService {

    private static final Set<String> ALLOWED_IMG_EXT = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    /** 临时图片 URL 前缀：/api/image/tmp/{yyyy-M-d}/... */
    private static final Pattern TMP_URL_PATTERN =
            Pattern.compile("/api/image/tmp/(\\d+-\\d+-\\d+)/([A-Za-z0-9\\-_]+\\.[A-Za-z0-9]+)");

    private static String todayFolder() {
        LocalDate today = LocalDate.now();
        return today.getYear() + "-" + today.getMonthValue() + "-" + today.getDayOfMonth();
    }

    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10MB

    @Value("${codeknest.upload.image-path:./image}")
    private String imagePath;

    private final ImageDeleteProducer imageDeleteProducer;

    public LocalImageStorageServiceImpl(ImageDeleteProducer imageDeleteProducer) {
        this.imageDeleteProducer = imageDeleteProducer;
    }

    @Override
    public String uploadTemp(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片不能超过 10MB");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        if (!ALLOWED_IMG_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 jpg/jpeg/png/gif/webp 格式");
        }

        try {
            String dateFolder = todayFolder();
            Path dir = Paths.get(imagePath, "tmp", dateFolder);
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "") + ext;
            Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            return "/api/image/tmp/" + dateFolder + "/" + filename;
        } catch (IOException e) {
            log.error("上传临时图片失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片上传失败");
        }
    }

    @Override
    public String confirmImages(String content, String refId) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(refId)) {
            return content;
        }
        Matcher m = TMP_URL_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String newUrl = moveTmpToPosts(m.group(1), m.group(2));
            m.appendReplacement(sb, Matcher.quoteReplacement(newUrl != null ? newUrl : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @Override
    public String confirmImage(String url, String refId) {
        if (!StringUtils.hasText(url) || !StringUtils.hasText(refId)) return url;
        Matcher m = TMP_URL_PATTERN.matcher(url);
        if (m.find()) {
            String newUrl = moveTmpToPosts(m.group(1), m.group(2));
            return newUrl != null ? newUrl : url;
        }
        return url;
    }

    /** 将单个临时文件迁移到正式目录（按年月日归档），返回新 URL；失败返回 null */
    private String moveTmpToPosts(String dateFolder, String filename) {
        String postsDateFolder = todayFolder();
        Path src = Paths.get(imagePath, "tmp", dateFolder, filename);
        Path postsDir = Paths.get(imagePath, "posts", postsDateFolder);
        Path dest = postsDir.resolve(filename);
        String newUrl = "/api/image/posts/" + postsDateFolder + "/" + filename;
        if (Files.exists(src)) {
            try {
                Files.createDirectories(postsDir);
                Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                return newUrl;
            } catch (IOException e) {
                log.warn("确认图片失败: {}", src, e);
                return null;
            }
        }
        // 源已不存在：可能已被正文确认逻辑迁移过，若目标存在则直接返回新 URL
        if (Files.exists(dest)) {
            return newUrl;
        }
        return null;
    }

    @Override
    public void deleteTemp(List<String> urls) {
        // 异步删除：发送 MQ 消息，由消费者执行实际文件删除，不阻塞请求线程
        imageDeleteProducer.sendDelete(urls);
    }

    @Override
    public void doDeleteTemp(List<String> urls) {
        if (urls == null || urls.isEmpty()) return;
        for (String url : urls) {
            if (!StringUtils.hasText(url)) continue;
            Matcher m = TMP_URL_PATTERN.matcher(url);
            if (m.find()) {
                Path file = Paths.get(imagePath, "tmp", m.group(1), m.group(2));
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    log.debug("删除临时图片失败: {}", file, e);
                }
            }
        }
    }

    @Override
    public void cleanupTemp(int olderThanHours) {
        Path tmpDir = Paths.get(imagePath, "tmp");
        if (!Files.exists(tmpDir)) return;
        Instant threshold = Instant.now().minus(olderThanHours, ChronoUnit.HOURS);
        try {
            Files.walkFileTree(tmpDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        if (attrs.lastModifiedTime().toInstant().isBefore(threshold)) {
                            Files.delete(file);
                        }
                    } catch (IOException e) {
                        log.debug("清理临时图片失败: {}", file, e);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        try (var stream = Files.newDirectoryStream(dir)) {
                            if (!stream.iterator().hasNext()) {
                                Files.delete(dir);
                            }
                        }
                    } catch (IOException e) {
                        log.debug("清理空目录失败: {}", dir, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("遍历临时图片目录失败", e);
        }
    }

    /** 每小时清理一次超过 24 小时的临时图片 */
    @Scheduled(fixedDelay = 60 * 60 * 1000L)
    public void scheduledCleanup() {
        cleanupTemp(24);
    }
}
