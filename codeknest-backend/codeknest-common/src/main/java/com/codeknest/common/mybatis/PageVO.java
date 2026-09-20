package com.codeknest.common.mybatis;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页响应结构 {items, page, size, total, totalPages}
 */
@Data
public class PageVO<T> {

    private List<T> items;
    private long page;
    private long size;
    private long total;
    private long totalPages;

    public static <E, T> PageVO<T> of(IPage<E> p, List<T> items) {
        PageVO<T> vo = new PageVO<>();
        vo.setItems(items);
        vo.setPage(p.getCurrent());
        vo.setSize(p.getSize());
        vo.setTotal(p.getTotal());
        vo.setTotalPages(p.getPages());
        return vo;
    }

    public static <E, T> PageVO<T> of(IPage<E> p, Function<E, T> mapper) {
        return of(p, p.getRecords().stream().map(mapper).toList());
    }
}
