// 后端契约类型（与后端 VO 对齐）

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

// ---------- 搜索 ----------

export interface SearchParams {
  q: string
  page?: number
  size?: number
  sort?: 'relevance' | 'time' | 'views'
}

export interface SearchResultItem {
  id: number
  title: string
  summary?: string
  author?: { id: number; username: string }
  tags?: string[]
  viewCount?: number
  publishedAt?: string
}

export interface AuthorInfo {
  id: number
  username: string
  avatar?: string
}

export interface CategoryInfo {
  id: number
  name: string
}

export interface TagInfo {
  id: number
  name: string
}

export interface PostVO {
  id: number
  title: string
  summary?: string
  content?: string
  coverImage?: string
  author?: AuthorInfo
  category?: CategoryInfo
  tags: TagInfo[]
  viewCount: number
  likeCount: number
  commentCount: number
  favoriteCount: number
  isLiked?: boolean
  isFavorited?: boolean
  isFollowingAuthor?: boolean
  publishedAt?: string
  createdAt?: string
}

export interface PostQuery {
  page?: number
  size?: number
  q?: string
  tag?: string
  categoryId?: number
  userId?: number
  sort?: 'latest' | 'hot' | 'recommend'
}

export interface SavePostPayload {
  title: string
  summary?: string
  content: string
  categoryId?: number
  coverImage?: string
  tagIds?: number[]
  tagNames?: string[]
}

export interface DraftVO {
  /** 草稿 ID（MongoDB ObjectId 字符串） */
  id: string
  postId?: number
  title?: string
  content?: string
  summary?: string
  coverImage?: string
  tagNames?: string[]
  categoryId?: number
  createdAt?: string
  updatedAt?: string
}

export interface CommentVO {
  id: number
  postId: number
  parentId?: number
  userId: number
  username: string
  userAvatar?: string
  replyToUserId?: number
  replyToUsername?: string
  content: string
  likeCount: number
  isLiked?: boolean
  createdAt: string
  replies: CommentVO[]
}

export interface UserHomeVO {
  id: number
  username: string
  avatar?: string
  role?: string
  bio?: string
  website?: string
  location?: string
  company?: string
  github?: string
  followersCount: number
  followingCount: number
  postsCount: number
  following: boolean
}

export interface SimpleUser {
  id: number
  username: string
  avatar?: string
}

export interface NotificationVO {
  id: number
  type: string
  title?: string
  content?: string
  sourceUserId?: number
  sourceUsername?: string
  sourceUserAvatar?: string
  sourcePostId?: number
  sourceCommentId?: number
  isRead: boolean
  createdAt: string
}

export interface Category {
  id: number
  name: string
  slug?: string
  description?: string
  sortOrder?: number
}

export interface Tag {
  id: number
  name: string
  slug?: string
  postCount?: number
}

// ---------- 公告 ----------

export interface Notice {
  id: number
  title: string
  content: string
  status: number
  sortOrder: number
  createdAt?: string
}
