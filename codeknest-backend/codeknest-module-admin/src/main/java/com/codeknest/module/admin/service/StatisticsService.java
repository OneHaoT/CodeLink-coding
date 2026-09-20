package com.codeknest.module.admin.service;

import com.codeknest.module.admin.vo.OverviewVO;
import com.codeknest.module.admin.vo.TrendPointVO;

import java.util.List;

public interface StatisticsService {

    OverviewVO overview();

    List<TrendPointVO> trends(Integer days);
}
