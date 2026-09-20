package com.codeknest.module.admin.service;

import com.codeknest.module.admin.dto.SaveTagDTO;
import com.codeknest.module.content.post.entity.Tag;

import java.util.List;

public interface AdminTagService {

    List<Tag> listAll();

    Tag create(SaveTagDTO dto);

    Tag update(Long id, SaveTagDTO dto);

    void delete(Long id);
}
