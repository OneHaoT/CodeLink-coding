package com.codeknest.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.module.admin.dto.SaveCategoryDTO;
import com.codeknest.module.admin.service.AdminCategoryService;
import com.codeknest.module.admin.service.AuditLogService;
import com.codeknest.module.admin.support.AuditActions;
import com.codeknest.module.admin.support.Slugs;
import com.codeknest.module.content.post.entity.Category;
import com.codeknest.module.content.post.entity.Post;
import com.codeknest.module.content.post.mapper.CategoryMapper;
import com.codeknest.module.content.post.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminCategoryServiceImpl implements AdminCategoryService {

    private final CategoryMapper categoryMapper;
    private final PostMapper postMapper;
    private final AuditLogService auditLogService;

    @Override
    public List<Category> listAll() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));
    }

    @Override
    public Category create(SaveCategoryDTO dto) {
        String name = requireName(dto.getName());
        if (categoryMapper.selectCount(new LambdaQueryWrapper<Category>().eq(Category::getName, name)) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分类名已存在");
        }
        String slug = resolveSlug(dto.getSlug(), name);
        ensureSlugUnique(slug, null);

        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        category.setDescription(StringUtils.hasText(dto.getDescription()) ? dto.getDescription().trim() : null);
        category.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        category.setCreatedAt(LocalDateTime.now());
        categoryMapper.insert(category);

        auditLogService.record(AuditActions.CATEGORY_CREATE, "category:" + category.getId(), name);
        return category;
    }

    @Override
    public Category update(Long id, SaveCategoryDTO dto) {
        Category existing = requireCategory(id);
        String name = requireName(dto.getName());
        if (!name.equals(existing.getName())
                && categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                        .eq(Category::getName, name).ne(Category::getId, id)) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分类名已存在");
        }
        String slug;
        if (StringUtils.hasText(dto.getSlug())) {
            slug = normalizeProvidedSlug(dto.getSlug());
            ensureSlugUnique(slug, id);
        } else {
            slug = existing.getSlug();
        }

        existing.setName(name);
        existing.setSlug(slug);
        existing.setDescription(StringUtils.hasText(dto.getDescription()) ? dto.getDescription().trim() : null);
        existing.setSortOrder(dto.getSortOrder() == null ? existing.getSortOrder() : dto.getSortOrder());
        categoryMapper.updateById(existing);

        auditLogService.record(AuditActions.CATEGORY_UPDATE, "category:" + id, name);
        return existing;
    }

    @Override
    public void delete(Long id) {
        Category existing = requireCategory(id);
        Integer cid = Math.toIntExact(id);
        // 该分类下文章置为「未分类」，再物理删除分类
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getCategoryId, cid)
                .set(Post::getCategoryId, null));
        categoryMapper.deleteById(id);

        auditLogService.record(AuditActions.CATEGORY_DELETE, "category:" + id, existing.getName());
    }

    // ---------- private ----------

    private Category requireCategory(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }

    private String requireName(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分类名不能为空");
        }
        return raw.trim();
    }

    private String resolveSlug(String provided, String name) {
        if (StringUtils.hasText(provided)) {
            return normalizeProvidedSlug(provided);
        }
        return Slugs.generate(name, "cat-");
    }

    private String normalizeProvidedSlug(String raw) {
        String slug = raw.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "slug 格式不合法，仅允许字母数字与连字符");
        }
        return slug;
    }

    private void ensureSlugUnique(String slug, Long excludeId) {
        LambdaQueryWrapper<Category> w = new LambdaQueryWrapper<Category>().eq(Category::getSlug, slug);
        if (excludeId != null) w.ne(Category::getId, excludeId);
        if (categoryMapper.selectCount(w) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "slug 已存在");
        }
    }
}
