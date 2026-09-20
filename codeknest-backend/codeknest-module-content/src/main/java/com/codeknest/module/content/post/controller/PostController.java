package com.codeknest.module.content.post.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.content.post.dto.PostQueryDTO;
import com.codeknest.module.content.post.dto.SaveDraftDTO;
import com.codeknest.module.content.post.dto.SavePostDTO;
import com.codeknest.module.content.post.service.ImageStorageService;
import com.codeknest.module.content.post.service.PostService;
import com.codeknest.module.content.post.vo.DraftVO;
import com.codeknest.module.content.post.vo.PostVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 文章控制器
 */
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final ImageStorageService imageStorageService;

    /** GET /api/posts — 列表（分页/标签/分类/关键词/作者） */
    @GetMapping
    public Result<PageVO<PostVO>> list(PostQueryDTO query) {
        return Result.ok(postService.list(query, SecurityContext.getUserId()));
    }

    /** GET /api/posts/{id} — 详情 */
    @GetMapping("/{id}")
    public Result<PostVO> detail(@PathVariable Long id) {
        return Result.ok(postService.detail(id, SecurityContext.getUserId()));
    }

    /** POST /api/posts — 发布 */
    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody SavePostDTO dto) {
        Long id = postService.createPost(SecurityContext.requireUserId(), dto);
        return Result.created(Map.of("postId", id));
    }

    /** POST /api/posts/upload-image — 上传正文图片（临时存储，发布/存草稿时确认） */
    @PostMapping("/upload-image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = imageStorageService.uploadTemp(file, SecurityContext.requireUserId());
        return Result.ok(Map.of("url", url));
    }

    /** DELETE /api/posts/upload-image — 删除临时图片（取消编辑时清理本次上传的图片） */
    @DeleteMapping("/upload-image")
    public Result<Void> deleteTempImages(@RequestBody Map<String, List<String>> body) {
        imageStorageService.deleteTemp(body.get("urls"));
        return Result.ok();
    }

    /** PUT /api/posts/{id} — 更新 */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SavePostDTO dto) {
        postService.updatePost(SecurityContext.requireUserId(), id, dto);
        return Result.ok("更新成功", null);
    }

    /** DELETE /api/posts/{id} — 删除（逻辑删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        postService.deletePost(SecurityContext.requireUserId(), id);
        return Result.ok("已删除", null);
    }

    // ---------- 草稿 ----------

    @GetMapping("/drafts")
    public Result<List<DraftVO>> drafts() {
        return Result.ok(postService.listDrafts(SecurityContext.requireUserId()));
    }

    @GetMapping("/drafts/{id}")
    public Result<DraftVO> draftDetail(@PathVariable String id) {
        return Result.ok(postService.draftDetail(SecurityContext.requireUserId(), id));
    }

    @PostMapping("/drafts")
    public Result<Map<String, String>> saveDraft(@RequestBody SaveDraftDTO dto) {
        String id = postService.saveDraft(SecurityContext.requireUserId(), dto);
        return Result.ok(Map.of("id", id));
    }

    @DeleteMapping("/drafts/{id}")
    public Result<Void> deleteDraft(@PathVariable String id) {
        postService.deleteDraft(SecurityContext.requireUserId(), id);
        return Result.ok();
    }
}
