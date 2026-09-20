package com.codeknest.module.account.auth.service;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.account.auth.vo.LoginLogVO;

public interface LoginLogService {

    /** 记录登录日志（任何异常都不影响登录主流程） */
    void record(Long userId, String account, boolean success, String failReason);

    /** 分页查询（管理端）：按账号模糊、结果精确筛选 */
    PageVO<LoginLogVO> page(int page, int size, String account, Boolean success);
}
