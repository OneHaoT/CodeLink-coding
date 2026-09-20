package com.codeknest.module.admin.service;

import com.codeknest.module.admin.dto.SaveCategoryDTO;
import com.codeknest.module.content.post.entity.Category;

import java.util.List;

public interface AdminCategoryService {

    List<Category> listAll();

    Category create(SaveCategoryDTO dto);

    Category update(Long id, SaveCategoryDTO dto);

    void delete(Long id);
}
