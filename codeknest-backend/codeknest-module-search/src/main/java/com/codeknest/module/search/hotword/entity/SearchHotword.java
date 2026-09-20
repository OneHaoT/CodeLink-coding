package com.codeknest.module.search.hotword.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 搜索热词统计表 t_search_hotword
 * <p>
 * 注意：本表是热词的**权威源**（Redis 只做增量累加与读加速）；表无逻辑删除列，
 * 因此不继承 BaseEntity（否则 MyBatis-Plus 会拼出 deleted=0 条件导致 SQL 报错）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_search_hotword")
public class SearchHotword {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 搜索关键词（唯一） */
    private String keyword;

    /** 累计被搜索次数 */
    private Long searchCount;

    /** 最近一次被搜索时间 */
    private LocalDateTime lastSearchedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SearchHotword(String keyword, Long searchCount, LocalDateTime lastSearchedAt) {
        this.keyword = keyword;
        this.searchCount = searchCount;
        this.lastSearchedAt = lastSearchedAt;
    }
}