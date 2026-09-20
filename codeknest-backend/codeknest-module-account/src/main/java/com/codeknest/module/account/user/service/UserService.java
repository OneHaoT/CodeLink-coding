package com.codeknest.module.account.user.service;

import com.codeknest.module.account.user.dto.UpdateProfileDTO;
import com.codeknest.module.account.user.vo.SimpleUserVO;
import com.codeknest.module.account.user.vo.UserHomeVO;

import java.util.List;

public interface UserService {

    /** 获取用户主页（currentUserId 可为 null=游客） */
    UserHomeVO getUserHome(Long userId, Long currentUserId);

    /** 获取当前登录用户资料（不存在则初始化 profile 行） */
    UserHomeVO getMyProfile(Long currentUserId);

    /** 更新当前用户资料 */
    UserHomeVO updateMyProfile(Long currentUserId, UpdateProfileDTO dto);

    List<SimpleUserVO> listFollowers(Long userId);

    List<SimpleUserVO> listFollowing(Long userId);

    /** 关注，返回最新粉丝数 */
    int follow(Long followerId, Long followingId);

    /** 取消关注，返回最新粉丝数 */
    int unfollow(Long followerId, Long followingId);

    /** 是否已关注 */
    boolean isFollowing(Long followerId, Long followingId);

    /** 确保 profile 行存在（懒初始化） */
    void ensureProfile(Long userId);
}
