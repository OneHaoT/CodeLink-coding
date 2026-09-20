package com.codeknest.module.account.actionlog.service;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.account.actionlog.vo.UserActionLogVO;
import com.codeknest.module.account.event.UserEventMessage;

import java.time.LocalDate;

/**
 * 通用用户操作日志服务（MongoDB user_action_log）
 */
public interface UserActionLogService {

    /** 落库一条操作日志（消费端调用，失败仅记日志） */
    void save(UserEventMessage message);

    /** 后台分页查询：动作类型 / 账号模糊 / 用户 id / 时间区间 */
    PageVO<UserActionLogVO> page(int page, int size, String action, String account,
                                Long userId, LocalDate start, LocalDate end);
}