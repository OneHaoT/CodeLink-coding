// 与后端统一契约对齐

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

// ---------- 仪表盘 ----------

export interface Overview {
  userCount: number
  postCount: number
  commentCount: number
  totalViews: number
  todayNewUsers: number
  todayNewPosts: number
  pendingPosts: number
}

export interface TrendPoint {
  date: string
  newUsers: number
  newPosts: number
  newComments: number
}

// ---------- 文章 ----------

export interface SimpleAuthor {
  id: number
  username: string
}

export interface SimpleCategory {
  id: number
  name: string
}

/** 0=已删除 1=已发布 2=待审核 3=审核拒绝 */
export type PostStatus = 0 | 1 | 2 | 3

export interface AdminPost {
  id: number
  title: string
  author: SimpleAuthor
  category: SimpleCategory | null
  status: PostStatus
  viewCount: number
  likeCount: number
  commentCount: number
  favoriteCount: number
  createdAt: string
}

export interface AdminPostQuery {
  page?: number
  size?: number
  q?: string
  status?: number
  userId?: number
  categoryId?: number
}

export interface AuditPostPayload {
  status: 1 | 3
  reason?: string
}

// ---------- 评论 ----------

export interface AdminComment {
  id: number
  content: string
  postId: number
  postTitle: string
  userId: number
  username: string
  likeCount: number
  status: number
  createdAt: string
}

export interface AdminCommentQuery {
  page?: number
  size?: number
  q?: string
  postId?: number
  userId?: number
  status?: number
}

// ---------- 用户 ----------

export interface AdminUser {
  id: number
  username: string
  email: string
  role: string
  status: number
  lastLoginAt: string | null
  createdAt: string
}

export interface AdminUserQuery {
  page?: number
  size?: number
  q?: string
  status?: number
}

// ---------- 标签 / 分类 ----------

export interface Tag {
  id: number
  name: string
  slug: string
  postCount: number
  createdAt: string
}

export interface TagPayload {
  name: string
  slug?: string
}

export interface Category {
  id: number
  name: string
  slug: string
  description: string | null
  sortOrder: number
  createdAt: string
}

export interface CategoryPayload {
  name: string
  slug?: string
  description?: string
  sortOrder?: number
}

// ---------- 敏感词 / 审计 ----------

export interface SensitiveWord {
  id: number
  word: string
  category: string | null
  createdAt: string
}

export interface SensitiveWordQuery {
  page?: number
  size?: number
  word?: string
  category?: string
}

export interface AuditLog {
  id: number
  userId: number
  operatorName: string
  action: string
  target: string
  ip: string
  detail: string | null
  createdAt: string
}

/** 登录日志（MongoDB login_log 集合，id 为 ObjectId 字符串） */
export interface LoginLog {
  id: string
  userId: number | null
  account: string
  success: boolean
  failReason: string | null
  ip: string | null
  userAgent: string | null
  createdAt: string
}

export interface LoginLogQuery {
  page?: number
  size?: number
  account?: string
  success?: boolean
}

export interface AuditLogQuery {
  page?: number
  size?: number
  action?: string
  userId?: number
  startDate?: string
  endDate?: string
}

// ---------- 公告 ----------

export interface Notice {
  id: number
  title: string
  content: string
  /** 1=已发布 0=下架 */
  status: number
  sortOrder: number
  createdAt?: string
  updatedAt?: string
}

export interface NoticePayload {
  title: string
  content: string
  status?: 0 | 1
  sortOrder?: number
}
