package com.codeknest.module.account.auth.service;

import com.codeknest.module.account.auth.dto.LoginDTO;
import com.codeknest.module.account.auth.dto.PasswordChangeDTO;
import com.codeknest.module.account.auth.dto.RegisterDTO;
import com.codeknest.module.account.auth.vo.LoginVO;
import com.codeknest.module.account.auth.vo.RegisterVO;

/**
 * 认证服务接口
 */
public interface AuthService {

    /** 注册 */
    RegisterVO register(RegisterDTO dto);

    /** 登录 */
    LoginVO login(LoginDTO dto);

    /** 用 refreshToken 换 accessToken */
    LoginVO refresh(String refreshToken);

    /** 登出 — 将 refreshToken 加入黑名单 */
    void logout(Long userId);

    /** 修改密码 */
    void changePassword(Long userId, PasswordChangeDTO dto);
}
