package com.codeknest.module.admin.service;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.admin.dto.AdminUserQueryDTO;
import com.codeknest.module.admin.vo.AdminUserVO;

public interface AdminUserService {

    PageVO<AdminUserVO> page(AdminUserQueryDTO query);

    void updateStatus(Long id, Integer status);

    void resetPassword(Long id, String newPassword);
}
