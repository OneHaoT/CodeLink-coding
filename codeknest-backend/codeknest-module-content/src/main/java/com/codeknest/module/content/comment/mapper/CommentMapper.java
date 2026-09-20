package com.codeknest.module.content.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeknest.module.content.comment.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
