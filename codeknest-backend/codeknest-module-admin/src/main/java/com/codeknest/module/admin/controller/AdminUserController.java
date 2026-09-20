package com.codeknest.module.admin.controller;

import com.codeknest.common.core.Result;
import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.admin.dto.AdminUserQueryDTO;
import com.codeknest.module.admin.dto.ResetPasswordDTO;
import com.codeknest.module.admin.dto.UpdateUserStatusDTO;
import com.codeknest.module.admin.service.AdminUserService;
import com.codeknest.module.admin.vo.AdminUserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理 /admin/users
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Result<PageVO<AdminUserVO>> list(AdminUserQueryDTO query) {
        return Result.ok(adminUserService.page(query));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody UpdateUserStatusDTO dto) {
        adminUserService.updateStatus(id, dto.getStatus());
        return Result.ok("状态已更新", null);
    }

    @PutMapping("/{id}/password/reset")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordDTO dto) {
        adminUserService.resetPassword(id, dto.getNewPassword());
        return Result.ok("密码已重置", null);
    }
}
