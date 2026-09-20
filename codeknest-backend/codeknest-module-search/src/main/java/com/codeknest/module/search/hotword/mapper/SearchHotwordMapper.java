package com.codeknest.module.search.hotword.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codeknest.module.search.hotword.entity.SearchHotword;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 搜索热词 Mapper（t_search_hotword）
 */
@Mapper
public interface SearchHotwordMapper extends BaseMapper<SearchHotword> {

    /**
     * 批量累加落库：按 keyword 唯一键冲突时累加计数、取较晚的搜索时间
     *
     * @param rows 待落库的增量行（每行 searchCount 为本轮增量）
     * @return 受影响行数
     */
    @Insert("<script>"
            + "INSERT INTO t_search_hotword (keyword, search_count, last_searched_at) VALUES "
            + "<foreach collection='rows' item='r' separator=','>"
            + "(#{r.keyword}, #{r.searchCount}, #{r.lastSearchedAt})"
            + "</foreach> "
            + "ON DUPLICATE KEY UPDATE "
            + "search_count = search_count + VALUES(search_count), "
            + "last_searched_at = GREATEST(last_searched_at, VALUES(last_searched_at))"
            + "</script>")
    int upsertBatch(@Param("rows") List<SearchHotword> rows);
}