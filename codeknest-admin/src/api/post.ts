import { http } from './request'
import type { AdminPost, AdminPostQuery, AuditPostPayload, PageResult } from './types'

export function getPosts(params: AdminPostQuery) {
  return http.get<PageResult<AdminPost>>('/admin/posts', { params })
}

export function auditPost(id: number, payload: AuditPostPayload) {
  return http.put(`/admin/posts/${id}/audit`, payload)
}

export function deletePost(id: number) {
  return http.delete(`/admin/posts/${id}`)
}
