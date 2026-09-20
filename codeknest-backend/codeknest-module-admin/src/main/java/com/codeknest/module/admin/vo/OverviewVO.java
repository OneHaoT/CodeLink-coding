package com.codeknest.module.admin.vo;

import lombok.Data;

/**
 * 仪表盘概览指标
 */
@Data
public class OverviewVO {

    private Long userCount;
    private Long postCount;
    private Long commentCount;
    private Long totalViews;

    private Long todayNewUsers;
    private Long todayNewPosts;
    private Long pendingPosts;
}
