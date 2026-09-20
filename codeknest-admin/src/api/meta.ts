import { http } from './request'
import type { Category, CategoryPayload, Tag, TagPayload } from './types'

// ---------- 标签 ----------

export function getTags() {
  return http.get<Tag[]>('/admin/tags')
}

export function createTag(payload: TagPayload) {
  return http.post<Tag>('/admin/tags', payload)
}

export function updateTag(id: number, payload: TagPayload) {
  return http.put<Tag>(`/admin/tags/${id}`, payload)
}

export function deleteTag(id: number) {
  return http.delete(`/admin/tags/${id}`)
}

// ---------- 分类 ----------

export function getCategories() {
  return http.get<Category[]>('/admin/categories')
}

export function createCategory(payload: CategoryPayload) {
  return http.post<Category>('/admin/categories', payload)
}

export function updateCategory(id: number, payload: CategoryPayload) {
  return http.put<Category>(`/admin/categories/${id}`, payload)
}

export function deleteCategory(id: number) {
  return http.delete(`/admin/categories/${id}`)
}
