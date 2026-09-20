package com.codeknest.module.admin.service;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.content.post.entity.SensitiveWord;

public interface SensitiveWordService {

    PageVO<SensitiveWord> page(String word, String category, Long page, Long size);

    SensitiveWord create(SensitiveWord req);

    void update(Long id, SensitiveWord req);

    void delete(Long id);
}
