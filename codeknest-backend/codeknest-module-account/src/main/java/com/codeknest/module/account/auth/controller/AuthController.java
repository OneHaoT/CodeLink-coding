package com.codeknest.module.account.auth.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.security.SecurityContext;
import com.codeknest.module.account.auth.dto.LoginDTO;
import com.codeknest.module.account.auth.dto.PasswordChangeDTO;
import com.codeknest.module.account.auth.dto.RegisterDTO;
import com.codeknest.module.account.auth.service.AuthService;
import com.codeknest.module.account.auth.vo.LoginVO;
import com.codeknest.module.account.auth.vo.RegisterVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** POST /api/auth/register — 注册 */
    @PostMapping("/register")
    public Result<RegisterVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.created(authService.register(dto));
    }

    /** POST /api/auth/login — 登录 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    /** POST /api/auth/refresh — 刷新 Token */
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Result.fail(40001, "refreshToken 不能为空");
        }
        return Result.ok(authService.refresh(refreshToken));
    }

    /** POST /api/auth/logout — 登出 */
    @PostMapping("/logout")
    public Result<Void> logout() {
        Long userId = SecurityContext.requireUserId();
        authService.logout(userId);
        return Result.ok();
    }

    /** PUT /api/auth/password/change — 修改密码 */
    @PutMapping("/password/change")
    public Result<Void> changePassword(@Valid @RequestBody PasswordChangeDTO dto) {
        Long userId = SecurityContext.requireUserId();
        authService.changePassword(userId, dto);
        return Result.ok("密码修改成功，已强制登出", null);
    }
}
