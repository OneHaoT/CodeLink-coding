package com.codeknest.module.admin.support;

import java.util.UUID;

/**
 * slug 生成规则（与 PostServiceImpl 新建标签保持一致）
 */
public final class Slugs {

    private Slugs() {}

    /**
     * 英文/数字直接转 kebab-case；中文等非 ASCII 名称用 hash 兜底。
     *
     * @param fallbackPrefix slug 前缀（如 tag-/cat-）
     */
    public static String generate(String name, String fallbackPrefix) {
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            slug = fallbackPrefix + Integer.toHexString(name.hashCode())
                    + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return slug;
    }
}
