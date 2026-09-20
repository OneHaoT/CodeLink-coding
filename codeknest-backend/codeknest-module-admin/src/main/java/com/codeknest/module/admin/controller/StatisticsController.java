package com.codeknest.module.admin.controller;

import com.codeknest.common.core.Result;
import com.codeknest.module.admin.service.StatisticsService;
import com.codeknest.module.admin.vo.OverviewVO;
import com.codeknest.module.admin.vo.TrendPointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 仪表盘统计 /admin/statistics
 */
@RestController
@RequestMapping("/admin/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/overview")
    public Result<OverviewVO> overview() {
        return Result.ok(statisticsService.overview());
    }

    @GetMapping("/trends")
    public Result<List<TrendPointVO>> trends(@RequestParam(required = false) Integer days) {
        return Result.ok(statisticsService.trends(days));
    }
}
