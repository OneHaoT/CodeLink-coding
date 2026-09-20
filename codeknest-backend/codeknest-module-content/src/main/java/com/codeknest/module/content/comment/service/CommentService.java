package com.codeknest.module.content.comment.service;

import com.codeknest.module.content.comment.dto.CreateCommentDTO;
import com.codeknest.module.content.comment.vo.CommentVO;

import java.util.List;

public interface CommentService {

    /** 某篇文章的评论树（二级嵌套，时间正序） */
    List<CommentVO> listByPost(Long postId, Long currentUserId);

    Long create(Long userId, CreateCommentDTO dto);

    void delete(Long userId, Long commentId);

    int like(Long userId, Long commentId);

    int unlike(Long userId, Long commentId);
}
