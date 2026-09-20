import { http } from './request'
import type {
  AuditLog,
  AuditLogQuery,
  Notice,
  NoticePayload,
  PageResult,
  SensitiveWord,
  SensitiveWordQuery,
  UserActionLog,
  UserActionLogQuery,
} from './types'

// ---------- 敏感词 ----------

export function getSensitiveWords(params: SensitiveWordQuery) {
  return http.get<PageResult<SensitiveWord>>('/admin/sensitive-words', { params })
}

export function createSensitiveWord(payload: Pick<SensitiveWord, 'word' | 'category'>) {
  return http.post<SensitiveWord>('/admin/sensitive-words', payload)
}

export function updateSensitiveWord(id: number, payload: Pick<SensitiveWord, 'word' | 'category'>) {
  return http.put(`/admin/sensitive-words/${id}`, payload)
}

export function deleteSensitiveWord(id: number) {
  return http.delete(`/admin/sensitive-words/${id}`)
}

// ---------- 审计日志 ----------

export function getAuditLogs(params: AuditLogQuery) {
  return http.get<PageResult<AuditLog>>('/admin/audit-logs', { params })
}

// ---------- 操作日志（MongoDB） ----------

export function getActionLogs(params: UserActionLogQuery) {
  return http.get<PageResult<UserActionLog>>('/admin/action-logs', { params })
}

// ---------- 公告 ----------

export function getNotices() {
  return http.get<Notice[]>('/admin/notices')
}

export function createNotice(payload: NoticePayload) {
  return http.post<Notice>('/admin/notices', payload)
}

export function updateNotice(id: number, payload: NoticePayload) {
  return http.put<Notice>(`/admin/notices/${id}`, payload)
}

export function toggleNoticeStatus(id: number, status: 0 | 1) {
  return http.put(`/admin/notices/${id}/status?status=${status}`)
}

export function deleteNotice(id: number) {
  return http.delete(`/admin/notices/${id}`)
}
