import { http } from './request'
import type { AdminComment, AdminCommentQuery, PageResult } from './types'

export function getComments(params: AdminCommentQuery) {
  return http.get<PageResult<AdminComment>>('/admin/comments', { params })
}

export function deleteComment(id: number) {
  return http.delete(`/admin/comments/${id}`)
}
