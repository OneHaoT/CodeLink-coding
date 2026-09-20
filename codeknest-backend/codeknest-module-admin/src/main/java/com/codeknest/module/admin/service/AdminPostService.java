package com.codeknest.module.admin.service;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.admin.dto.AdminPostQueryDTO;
import com.codeknest.module.admin.vo.AdminPostVO;

public interface AdminPostService {

    PageVO<AdminPostVO> page(AdminPostQueryDTO query);

    /** 审核：status 仅 1/3 */
    void audit(Long id, Integer status, String reason);

    /** 逻辑删除 */
    void delete(Long id);
}
