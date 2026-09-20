package com.codeknest.module.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计查询（原生聚合 SQL）
 */
@Mapper
public interface StatisticsMapper {

    /** 总浏览量（含全部未逻辑删除的文章） */
    @Select("SELECT COALESCE(SUM(view_count), 0) FROM t_post WHERE deleted = 0")
    Long sumPostViews();

    /** 每日新增用户 */
    @Select("SELECT DATE(created_at) AS d, COUNT(*) AS c FROM t_user " +
            "WHERE deleted = 0 AND created_at >= #{from} GROUP BY DATE(created_at)")
    List<Map<String, Object>> dailyUsers(@Param("from") LocalDateTime from);

    /** 每日新增已发布文章 */
    @Select("SELECT DATE(created_at) AS d, COUNT(*) AS c FROM t_post " +
            "WHERE deleted = 0 AND status = 1 AND created_at >= #{from} GROUP BY DATE(created_at)")
    List<Map<String, Object>> dailyPosts(@Param("from") LocalDateTime from);

    /** 每日新增正常评论 */
    @Select("SELECT DATE(created_at) AS d, COUNT(*) AS c FROM t_comment " +
            "WHERE deleted = 0 AND status = 1 AND created_at >= #{from} GROUP BY DATE(created_at)")
    List<Map<String, Object>> dailyComments(@Param("from") LocalDateTime from);
}
