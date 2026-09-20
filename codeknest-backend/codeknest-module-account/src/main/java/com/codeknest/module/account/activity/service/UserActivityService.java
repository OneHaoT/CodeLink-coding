package com.codeknest.module.account.activity.service;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.account.activity.vo.UserActivityVO;
import com.codeknest.module.account.event.UserEventMessage;

/**
 * 用户动态服务（MongoDB user_activity）
 */
public interface UserActivityService {

    /** 落库一条动态（消费端调用，仅 feed 类动作） */
    void save(UserEventMessage message);

    /** 某用户的动态（公开，个人主页动态区） */
    PageVO<UserActivityVO> pageByUser(Long userId, int page, int size);

    /** 关注流：我 + 我关注的人（关注关系实时取自 MySQL，取关后立即生效） */
    PageVO<UserActivityVO> pageFollowing(Long currentUserId, int page, int size);
}