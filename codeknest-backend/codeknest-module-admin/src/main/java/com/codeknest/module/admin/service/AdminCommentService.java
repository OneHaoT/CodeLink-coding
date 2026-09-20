package com.codeknest.module.admin.service;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.admin.dto.AdminCommentQueryDTO;
import com.codeknest.module.admin.vo.AdminCommentVO;

public interface AdminCommentService {

    PageVO<AdminCommentVO> page(AdminCommentQueryDTO query);

    void delete(Long id);
}
