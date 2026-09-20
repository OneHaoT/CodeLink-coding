import type { UserActivityVO } from '@/api/types'

/**
 * 用户动态（MongoDB user_activity 投影）的展示文案与跳转目标。
 * 文案措辞与后端 UserActionLogServiceImpl.describe 保持一致。
 */
const TEXTS: Record<string, (a: UserActivityVO) => string> = {
  POST_PUBLISH: (a) => `发表了文章《${a.postTitle || ''}》`,
  COMMENT_CREATE: (a) => `评论了《${a.postTitle || ''}》：${a.commentExcerpt || ''}`,
  POST_LIKE: (a) => `点赞了《${a.postTitle || ''}》`,
  POST_FAVORITE: (a) => `收藏了《${a.postTitle || ''}》`,
  FOLLOW: (a) => `关注了 ${a.targetUsername || ''}`,
}

export function activityText(a: UserActivityVO): string {
  const build = TEXTS[a.action]
  return build ? build(a) : a.action
}

/** 可跳转路由：优先文章详情，关注类跳对方主页，其余无跳转 */
export function activityRoute(a: UserActivityVO): string | null {
  if (a.postId) return `/posts/${a.postId}`
  if (a.action === 'FOLLOW' && a.targetId) return `/users/${a.targetId}`
  return null
}