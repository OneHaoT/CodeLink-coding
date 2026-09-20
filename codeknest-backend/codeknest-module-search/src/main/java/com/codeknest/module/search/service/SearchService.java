package com.codeknest.module.search.service;

import com.codeknest.common.mybatis.PageVO;
import com.codeknest.module.search.vo.SearchVO;

public interface SearchService {

    /**
     * 全文检索已发布文章
     *
     * @param q    关键词，为空时返回空结果
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @param sort 排序：relevance（相关度）/ time（发布时间）/ views（浏览量）
     */
    PageVO<SearchVO> search(String q, Integer page, Integer size, String sort);

    /** 按 postId 重新读取 MySQL 并同步单篇文档（不存在或非已发布则删除文档） */
    void syncPost(Long postId);

    /** 全量重建索引，返回写入的文档数 */
    long reindex();

    /** 搜索热词 Top N（按近期搜索次数排序） */
    java.util.List<String> hotwords(int topN);
}
