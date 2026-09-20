package com.codeknest.module.account.user.controller;

import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.common.core.Result;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.account.user.dto.UpdateProfileDTO;
import com.codeknest.module.account.user.service.UserService;
import com.codeknest.module.account.user.vo.SimpleUserVO;
import com.codeknest.module.account.user.vo.UserHomeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 用户中心控制器
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Value("${codeknest.upload.local-path}")
    private String uploadPath;

    @Value("${codeknest.upload.access-url-prefix}")
    private String accessUrlPrefix;

    private static final Set<String> ALLOWED_IMG_EXT = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    /** GET /api/users/{id} — 用户主页（游客可访问） */
    @GetMapping("/{id}")
    public Result<UserHomeVO> getUserHome(@PathVariable Long id) {
        return Result.ok(userService.getUserHome(id, SecurityContext.getUserId()));
    }

    /** GET /api/users/me — 我的资料 */
    @GetMapping("/me")
    public Result<UserHomeVO> me() {
        return Result.ok(userService.getMyProfile(SecurityContext.requireUserId()));
    }

    /** PUT /api/users/me — 更新资料 */
    @PutMapping("/me")
    public Result<UserHomeVO> updateMe(@Valid @RequestBody UpdateProfileDTO dto) {
        return Result.ok(userService.updateMyProfile(SecurityContext.requireUserId(), dto));
    }

    /** POST /api/users/me/avatar — 上传头像 */
    @PostMapping("/me/avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        }
        if (!ALLOWED_IMG_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 jpg/png/gif/webp 格式");
        }
        Path dir = Paths.get(uploadPath, "avatars");
        Files.createDirectories(dir);
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        Files.copy(file.getInputStream(), dir.resolve(filename));

        String url = accessUrlPrefix + "/avatars/" + filename;
        Long userId = SecurityContext.requireUserId();
        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setAvatar(url);
        userService.updateMyProfile(userId, dto);
        return Result.ok(Map.of("url", url));
    }

    /** GET /api/users/{id}/followers */
    @GetMapping("/{id}/followers")
    public Result<List<SimpleUserVO>> followers(@PathVariable Long id) {
        return Result.ok(userService.listFollowers(id));
    }

    /** GET /api/users/{id}/following */
    @GetMapping("/{id}/following")
    public Result<List<SimpleUserVO>> following(@PathVariable Long id) {
        return Result.ok(userService.listFollowing(id));
    }

    /** POST /api/users/{id}/follow */
    @PostMapping("/{id}/follow")
    public Result<Map<String, Object>> follow(@PathVariable Long id) {
        int followers = userService.follow(SecurityContext.requireUserId(), id);
        return Result.ok(Map.of("followersCount", followers, "following", true));
    }

    /** DELETE /api/users/{id}/follow */
    @DeleteMapping("/{id}/follow")
    public Result<Map<String, Object>> unfollow(@PathVariable Long id) {
        int followers = userService.unfollow(SecurityContext.requireUserId(), id);
        return Result.ok(Map.of("followersCount", followers, "following", false));
    }

    /** GET /api/users/{id}/follow/status */
    @GetMapping("/{id}/follow/status")
    public Result<Map<String, Object>> followStatus(@PathVariable Long id) {
        Long me = SecurityContext.getUserId();
        return Result.ok(Map.of("following", me != null && userService.isFollowing(me, id)));
    }
}
