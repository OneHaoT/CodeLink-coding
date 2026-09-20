package com.codeknest.module.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 每日趋势点
 */
@Data
@AllArgsConstructor
public class TrendPointVO {

    /** ISO 日期 yyyy-MM-dd */
    private String date;

    private Long newUsers;
    private Long newPosts;
    private Long newComments;
}
