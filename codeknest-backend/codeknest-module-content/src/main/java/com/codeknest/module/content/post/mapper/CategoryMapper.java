package com.codeknest.module.content.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeknest.module.content.post.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
