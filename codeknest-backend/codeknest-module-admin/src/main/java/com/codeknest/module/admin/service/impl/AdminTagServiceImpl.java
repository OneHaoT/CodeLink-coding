package com.codeknest.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.module.admin.dto.SaveTagDTO;
import com.codeknest.module.admin.service.AdminTagService;
import com.codeknest.module.admin.service.AuditLogService;
import com.codeknest.module.admin.support.AuditActions;
import com.codeknest.module.admin.support.Slugs;
import com.codeknest.module.content.post.entity.PostTag;
import com.codeknest.module.content.post.entity.Tag;
import com.codeknest.module.content.post.mapper.PostTagMapper;
import com.codeknest.module.content.post.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminTagServiceImpl implements AdminTagService {

    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;
    private final AuditLogService auditLogService;

    @Override
    public List<Tag> listAll() {
        return tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .orderByDesc(Tag::getPostCount)
                .orderByAsc(Tag::getId));
    }

    @Override
    public Tag create(SaveTagDTO dto) {
        String name = requireName(dto.getName());
        if (tagMapper.selectCount(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name)) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名已存在");
        }
        String slug = resolveSlug(dto.getSlug(), name);
        ensureSlugUnique(slug, null);

        Tag tag = new Tag();
        tag.setName(name);
        tag.setSlug(slug);
        tag.setPostCount(0);
        tag.setCreatedAt(LocalDateTime.now());
        tagMapper.insert(tag);

        auditLogService.record(AuditActions.TAG_CREATE, "tag:" + tag.getId(), name);
        return tag;
    }

    @Override
    public Tag update(Long id, SaveTagDTO dto) {
        Tag existing = requireTag(id);
        String name = requireName(dto.getName());
        if (!name.equals(existing.getName())
                && tagMapper.selectCount(new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getName, name).ne(Tag::getId, id)) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名已存在");
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
        tagMapper.updateById(existing);

        auditLogService.record(AuditActions.TAG_UPDATE, "tag:" + id, name);
        return existing;
    }

    @Override
    public void delete(Long id) {
        Tag existing = requireTag(id);
        // 先清理文章-标签关联，再物理删除标签
        postTagMapper.delete(new LambdaQueryWrapper<PostTag>().eq(PostTag::getTagId, id));
        tagMapper.deleteById(id);

        auditLogService.record(AuditActions.TAG_DELETE, "tag:" + id, existing.getName());
    }

    // ---------- private ----------

    private Tag requireTag(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        return tag;
    }

    private String requireName(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名不能为空");
        }
        return raw.trim();
    }

    private String resolveSlug(String provided, String name) {
        if (StringUtils.hasText(provided)) {
            return normalizeProvidedSlug(provided);
        }
        return Slugs.generate(name, "tag-");
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
        LambdaQueryWrapper<Tag> w = new LambdaQueryWrapper<Tag>().eq(Tag::getSlug, slug);
        if (excludeId != null) w.ne(Tag::getId, excludeId);
        if (tagMapper.selectCount(w) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "slug 已存在");
        }
    }
}
