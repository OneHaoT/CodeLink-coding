import { http } from './request'
import type { AdminUser, AdminUserQuery, PageResult } from './types'

export function getUsers(params: AdminUserQuery) {
  return http.get<PageResult<AdminUser>>('/admin/users', { params })
}

export function updateUserStatus(id: number, status: 0 | 1) {
  return http.put(`/admin/users/${id}/status`, { status })
}

export function resetUserPassword(id: number, newPassword: string) {
  return http.put(`/admin/users/${id}/password/reset`, { newPassword })
}
