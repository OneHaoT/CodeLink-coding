import { http } from './request'
import type {
  Category,
  CommentVO,
  DraftVO,
  Notice,
  NotificationVO,
  PageResult,
  PostQuery,
  PostVO,
  SavePostPayload,
  SimpleUser,
  Tag,
  UserHomeVO,
} from './types'

// ---------- 文章 ----------
export const postApi = {
  list(params: PostQuery = {}) {
    return http.get<PageResult<PostVO>>('/posts', { params })
  },
  detail(id: number | string) {
    return http.get<PostVO>(`/posts/${id}`)
  },
  create(payload: SavePostPayload) {
    return http.post<{ postId: number }>('/posts', payload)
  },
  uploadImage(file: File) {
    const fd = new FormData()
    fd.append('file', file)
    return http.post<{ url: string }>('/posts/upload-image', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  deleteTempImages(urls: string[]) {
    return http.delete<null>('/posts/upload-image', { data: { urls } })
  },
  update(id: number, payload: SavePostPayload) {
    return http.put<null>(`/posts/${id}`, payload)
  },
  remove(id: number) {
    return http.delete<null>(`/posts/${id}`)
  },
}

// ---------- 草稿 ----------
export const draftApi = {
  list() {
    return http.get<DraftVO[]>('/posts/drafts')
  },
  detail(id: string) {
    return http.get<DraftVO>(`/posts/drafts/${id}`)
  },
  save(data: Partial<DraftVO>) {
    return http.post<{ id: string }>('/posts/drafts', data)
  },
  remove(id: string) {
    return http.delete<null>(`/posts/drafts/${id}`)
  },
}

// ---------- 点赞 / 收藏 ----------
export const interactionApi = {
  like(id: number) {
    return http.post<{ likeCount: number; isLiked: boolean }>(`/posts/${id}/like`)
  },
  unlike(id: number) {
    return http.delete<{ likeCount: number; isLiked: boolean }>(`/posts/${id}/like`)
  },
  favorite(id: number) {
    return http.post<{ favoriteCount: number; isFavorited: boolean }>(`/posts/${id}/favorite`)
  },
  unfavorite(id: number) {
    return http.delete<{ favoriteCount: number; isFavorited: boolean }>(`/posts/${id}/favorite`)
  },
  favorites() {
    return http.get<PostVO[]>('/posts/favorites')
  },
}

// ---------- 评论 ----------
export const commentApi = {
  list(postId: number) {
    return http.get<CommentVO[]>('/comments', { params: { postId } })
  },
  create(data: { postId: number; parentId?: number; replyToUserId?: number; content: string }) {
    return http.post<{ commentId: number }>('/comments', data)
  },
  remove(id: number) {
    return http.delete<null>(`/comments/${id}`)
  },
  like(id: number) {
    return http.post<{ likeCount: number; isLiked: boolean }>(`/comments/${id}/like`)
  },
  unlike(id: number) {
    return http.delete<{ likeCount: number; isLiked: boolean }>(`/comments/${id}/like`)
  },
}

// ---------- 标签 / 分类 ----------
export const metaApi = {
  tags() {
    return http.get<Tag[]>('/tags')
  },
  categories() {
    return http.get<Category[]>('/categories')
  },
}

// ---------- 用户 ----------
export const userApi = {
  home(id: number | string) {
    return http.get<UserHomeVO>(`/users/${id}`)
  },
  me() {
    return http.get<UserHomeVO>('/users/me')
  },
  updateMe(data: Partial<UserHomeVO & { avatar?: string }>) {
    return http.put<UserHomeVO>('/users/me', data)
  },
  followers(id: number) {
    return http.get<SimpleUser[]>(`/users/${id}/followers`)
  },
  following(id: number) {
    return http.get<SimpleUser[]>(`/users/${id}/following`)
  },
  follow(id: number) {
    return http.post<{ followersCount: number; following: boolean }>(`/users/${id}/follow`)
  },
  unfollow(id: number) {
    return http.delete<{ followersCount: number; following: boolean }>(`/users/${id}/follow`)
  },
  uploadAvatar(file: File) {
    const fd = new FormData()
    fd.append('file', file)
    return http.post<{ url: string }>('/users/me/avatar', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

// ---------- 公告 ----------
export const noticeApi = {
  list() {
    return http.get<Notice[]>('/notices')
  },
}

// ---------- 通知 ----------
export const notificationApi = {
  list(type?: string) {
    return http.get<NotificationVO[]>('/notifications', { params: type ? { type } : {} })
  },
  unreadCount() {
    return http.get<{ count: number }>('/notifications/unread/count')
  },
  markAllRead() {
    return http.put<null>('/notifications/read')
  },
  markRead(id: number) {
    return http.put<null>(`/notifications/${id}/read`)
  },
}
