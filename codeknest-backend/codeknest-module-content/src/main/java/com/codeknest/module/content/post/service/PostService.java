package com.codeknest.module.content.post.service;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.content.post.dto.PostQueryDTO;
import com.codeknest.module.content.post.dto.SaveDraftDTO;
import com.codeknest.module.content.post.dto.SavePostDTO;
import com.codeknest.module.content.post.vo.DraftVO;
import com.codeknest.module.content.post.vo.PostVO;

import java.util.List;

public interface PostService {

    PageVO<PostVO> list(PostQueryDTO query, Long currentUserId);

    /** 按给定 ID 顺序返回已发布文章（收藏列表等场景） */
    List<PostVO> listByIdsOrdered(List<Long> ids, Long currentUserId);

    PostVO detail(Long id, Long currentUserId);

    Long createPost(Long userId, SavePostDTO dto);

    void updatePost(Long userId, Long id, SavePostDTO dto);

    void deletePost(Long userId, Long id);

    List<DraftVO> listDrafts(Long userId);

    DraftVO draftDetail(Long userId, String id);

    String saveDraft(Long userId, SaveDraftDTO dto);

    void deleteDraft(Long userId, String id);
}
