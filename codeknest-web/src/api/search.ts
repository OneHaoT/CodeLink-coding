import { http } from './request'
import type { PageResult, SearchParams, SearchResultItem } from './types'

export const searchApi = {
  posts(params: SearchParams) {
    return http.get<PageResult<SearchResultItem>>('/search/posts', { params })
  },
  /** 搜索热词 Top10（后端 Redis ZSET 按热度倒序） */
  hotwords() {
    return http.get<string[]>('/search/hotwords')
  },
}
