<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import dayjs from 'dayjs'
import { commentApi, interactionApi, postApi, userApi } from '@/api/post'
import type { CommentVO, PostVO } from '@/api/types'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const postId = Number(route.params.id)

const post = ref<PostVO | null>(null)
const notFound = ref(false)
const comments = ref<CommentVO[]>([])

const commentText = ref('')
const replyMap = ref<Record<number, string>>({})
const replyOpen = ref<Record<number, boolean>>({})
const submitting = ref(false)

const isOwner = computed(
  () => userStore.userInfo != null && post.value?.author?.id === userStore.userInfo.id,
)
const commentCount = computed(() => {
  const n = (c: CommentVO[]): number => c.reduce((s, x) => s + 1 + n(x.replies || []), 0)
  return n(comments.value)
})

async function loadPost() {
  try {
    const res = await postApi.detail(postId)
    post.value = res.data
  } catch (e: any) {
    if (e?.bizCode === 40402 || e?.response?.status === 404) notFound.value = true
  }
}

async function loadComments() {
  const res = await commentApi.list(postId)
  comments.value = res.data || []
}

function requireLogin(): boolean {
  if (!userStore.isLoggedIn()) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}

async function toggleLike() {
  if (!post.value || !requireLogin()) return
  const p = post.value
  const res = p.isLiked ? await interactionApi.unlike(p.id) : await interactionApi.like(p.id)
  p.likeCount = res.data.likeCount
  p.isLiked = res.data.isLiked
}

async function toggleFavorite() {
  if (!post.value || !requireLogin()) return
  const p = post.value
  const res = p.isFavorited
    ? await interactionApi.unfavorite(p.id)
    : await interactionApi.favorite(p.id)
  p.favoriteCount = res.data.favoriteCount
  p.isFavorited = res.data.isFavorited
  ElMessage.success(p.isFavorited ? '已收藏' : '已取消收藏')
}

async function toggleFollow() {
  if (!post.value?.author || !requireLogin()) return
  const uid = post.value.author.id
  if (post.value.isFollowingAuthor) {
    const res = await userApi.unfollow(uid)
    post.value.isFollowingAuthor = res.data.following
  } else {
    const res = await userApi.follow(uid)
    post.value.isFollowingAuthor = res.data.following
  }
}

async function submitComment() {
  const content = commentText.value.trim()
  if (!content) return ElMessage.warning('评论内容不能为空')
  if (!requireLogin()) return
  submitting.value = true
  try {
    await commentApi.create({ postId, content })
    commentText.value = ''
    ElMessage.success('评论成功')
    await loadComments()
    if (post.value) post.value.commentCount = (post.value.commentCount || 0) + 1
  } finally {
    submitting.value = false
  }
}

function toggleReply(id: number) {
  replyOpen.value[id] = !replyOpen.value[id]
}

async function submitReply(root: CommentVO, replyTo?: CommentVO) {
  const content = (replyMap.value[root.id] || '').trim()
  if (!content) return ElMessage.warning('回复内容不能为空')
  if (!requireLogin()) return
  await commentApi.create({
    postId,
    content,
    parentId: root.id,
    replyToUserId: replyTo ? replyTo.userId : root.userId,
  })
  replyMap.value[root.id] = ''
  replyOpen.value[root.id] = false
  ElMessage.success('回复成功')
  await loadComments()
}

async function toggleCommentLike(c: CommentVO) {
  if (!requireLogin()) return
  const res = c.isLiked ? await commentApi.unlike(c.id) : await commentApi.like(c.id)
  c.likeCount = res.data.likeCount
  c.isLiked = res.data.isLiked
}

async function removeComment(c: CommentVO) {
  await ElMessageBox.confirm('确定删除这条评论吗？', '提示', { type: 'warning' })
  await commentApi.remove(c.id)
  ElMessage.success('已删除')
  await loadComments()
}

async function removePost() {
  if (!post.value) return
  await ElMessageBox.confirm('确定删除这篇文章吗？删除后不可恢复', '提示', { type: 'warning' })
  await postApi.remove(post.value.id)
  ElMessage.success('已删除')
  router.push('/posts')
}

function canDelete(c: CommentVO): boolean {
  const me = userStore.userInfo
  return !!me && (me.id === c.userId || me.role === 'ROLE_ADMIN')
}

function formatTime(iso?: string) {
  return iso ? dayjs(iso).format('YYYY年MM月DD日 HH:mm') : ''
}
function fromNow(iso: string) {
  return dayjs(iso).fromNow()
}

onMounted(async () => {
  await loadPost()
  if (post.value) await loadComments()
})
</script>

<template>
  <div class="detail-page">
    <template v-if="notFound">
      <div class="not-found">
        <el-icon class="nf-ic"><DocumentDelete /></el-icon>
        <h2>文章不存在或已删除</h2>
        <el-button type="primary" @click="router.push('/posts')">返回文章列表</el-button>
      </div>
    </template>

    <template v-else-if="post">
      <!-- 面包屑 -->
      <nav class="crumbs">
        <el-icon><HomeFilled /></el-icon>
        <router-link to="/">首页</router-link>
        <span class="sep">/</span>
        <router-link to="/posts">文章</router-link>
        <span class="sep">/</span>
        <span>{{ post.category?.name || '未分类' }}</span>
      </nav>

      <!-- 文章头 -->
      <header class="article-head">
        <div v-if="isOwner" class="owner-ops">
          <el-button size="small" @click="router.push(`/posts/${post.id}/edit`)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="removePost">删除</el-button>
        </div>
        <h1 class="art-title">{{ post.title }}</h1>
        <div class="art-meta">
          <div class="author-block">
            <el-avatar
              :size="44"
              class="art-avatar"
              @click="post.author && router.push(`/users/${post.author.id}`)"
            >
              {{ post.author?.username?.[0] || '?' }}
            </el-avatar>
            <div class="a-info">
              <div class="a-name" @click="post.author && router.push(`/users/${post.author.id}`)">
                {{ post.author?.username || '匿名' }}
              </div>
              <div class="a-time">
                {{ formatTime(post.publishedAt || post.createdAt) }} · 阅读
                {{ post.viewCount?.toLocaleString() }}
              </div>
            </div>
            <template v-if="!isOwner && userStore.isLoggedIn()">
              <el-button
                v-if="!post.isFollowingAuthor"
                size="small"
                type="primary"
                plain
                @click="toggleFollow"
                >+ 关注</el-button
              >
              <el-button v-else size="small" plain @click="toggleFollow">已关注</el-button>
            </template>
          </div>
          <div class="art-tags">
            <span v-for="t in post.tags || []" :key="t.id" class="tag">{{ t.name }}</span>
          </div>
        </div>
      </header>

      <!-- 正文 -->
      <article class="article-body">
        <MdPreview :model-value="post.content || post.summary || ''" preview-theme="github" />
      </article>

      <!-- 操作栏 -->
      <footer class="article-actions">
        <button class="act" :class="{ on: post.isLiked }" @click="toggleLike">
          <el-icon><ThumbsUpIcon :filled="post.isLiked" /></el-icon>
          <span>{{ post.likeCount }}</span>
        </button>
        <button class="act">
          <el-icon><ChatDotRound /></el-icon>
          <span>{{ commentCount }}</span>
        </button>
        <button class="act" :class="{ on: post.isFavorited }" @click="toggleFavorite">
          <el-icon><Collection /></el-icon>
          <span>{{ post.isFavorited ? '已收藏' : '收藏' }}</span>
        </button>
      </footer>

      <!-- 评论区 -->
      <section class="comments">
        <h3 class="c-title">
          <el-icon><ChatDotRound /></el-icon>评论 ({{ commentCount }})
        </h3>
        <div class="c-input">
          <el-input
            v-model="commentText"
            type="textarea"
            :rows="3"
            :placeholder="userStore.isLoggedIn() ? '说点什么...' : '登录后参与评论'"
            resize="none"
          />
          <div class="c-actions">
            <el-button type="primary" :loading="submitting" @click="submitComment"
              >发表评论</el-button
            >
          </div>
        </div>

        <div class="c-list">
          <div v-for="root in comments" :key="root.id" class="c-item">
            <el-avatar :size="36" @click="router.push(`/users/${root.userId}`)">
              {{ root.userAvatar || root.username[0] }}
            </el-avatar>
            <div class="c-body">
              <div class="c-header">
                <span class="c-author">{{ root.username }}</span>
                <span class="c-time">{{ fromNow(root.createdAt) }}</span>
              </div>
              <p class="c-text">{{ root.content }}</p>
              <div class="c-toolbar">
                <button
                  class="tb-btn"
                  :class="{ on: root.isLiked }"
                  @click="toggleCommentLike(root)"
                >
                  <el-icon><ThumbsUpIcon :filled="root.isLiked" /></el-icon>
                  <span>{{ root.likeCount || '' }}</span>
                </button>
                <button class="tb-btn" @click="toggleReply(root.id)">回复</button>
                <button v-if="canDelete(root)" class="tb-btn danger" @click="removeComment(root)">
                  删除
                </button>
              </div>

              <!-- 子回复 -->
              <div v-for="rp in root.replies || []" :key="rp.id" class="c-item sub">
                <el-avatar :size="28" @click="router.push(`/users/${rp.userId}`)">
                  {{ rp.userAvatar || rp.username[0] }}
                </el-avatar>
                <div class="c-body">
                  <div class="c-header">
                    <span class="c-author">{{ rp.username }}</span>
                    <template v-if="rp.replyToUsername">
                      <span class="reply-arrow">回复</span>
                      <span class="c-author">{{ rp.replyToUsername }}</span>
                    </template>
                    <span class="c-time">{{ fromNow(rp.createdAt) }}</span>
                  </div>
                  <p class="c-text">{{ rp.content }}</p>
                  <div class="c-toolbar">
                    <button
                      class="tb-btn"
                      :class="{ on: rp.isLiked }"
                      @click="toggleCommentLike(rp)"
                    >
                      <el-icon><ThumbsUpIcon :filled="rp.isLiked" /></el-icon>
                      <span>{{ rp.likeCount || '' }}</span>
                    </button>
                    <button class="tb-btn" @click="toggleReply(root.id)">回复</button>
                    <button v-if="canDelete(rp)" class="tb-btn danger" @click="removeComment(rp)">
                      删除
                    </button>
                  </div>
                </div>
              </div>

              <!-- 回复输入 -->
              <div v-if="replyOpen[root.id]" class="reply-box">
                <el-input
                  v-model="replyMap[root.id]"
                  type="textarea"
                  :rows="2"
                  :placeholder="`回复 @${root.username}`"
                  resize="none"
                  @keyup.enter.ctrl="submitReply(root)"
                />
                <div class="reply-actions">
                  <el-button size="small" @click="replyOpen[root.id] = false">取消</el-button>
                  <el-button size="small" type="primary" @click="submitReply(root)">发送</el-button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="!comments.length" class="c-empty">还没有评论，来抢沙发吧</div>
        </div>
      </section>
    </template>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.detail-page {
  max-width: 760px;
  margin: 0 auto;
  padding: $s-6 $s-4 $s-12;
}

.not-found {
  text-align: center;
  padding: $s-12 0;
  .nf-ic {
    font-size: 56px;
    color: $ink-4;
    margin-bottom: $s-4;
  }
  h2 {
    color: $ink-2;
    margin-bottom: $s-6;
  }
}

.crumbs {
  display: flex;
  align-items: center;
  gap: $s-2;
  font-size: $fs-sm;
  color: $ink-3;
  margin-bottom: $s-6;
  a {
    color: $ink-2;
    &:hover {
      color: $brand;
    }
  }
  .sep {
    color: $border-strong;
  }
}

.article-head {
  margin-bottom: $s-8;

  .owner-ops {
    display: flex;
    justify-content: flex-end;
    gap: $s-2;
    margin-bottom: $s-3;
  }

  .art-title {
    font-family: $font-display;
    font-size: 32px;
    font-weight: 800;
    line-height: 1.3;
    margin: 0 0 $s-5;
    color: $ink;
    letter-spacing: -0.01em;
  }

  .art-meta {
    padding-bottom: $s-5;
    border-bottom: 1px solid $border;
  }

  .author-block {
    display: flex;
    align-items: center;
    gap: $s-3;
    margin-bottom: $s-4;
    .art-avatar {
      cursor: pointer;
      flex-shrink: 0;
    }
    .a-info {
      flex: 1;
    }
    .a-name {
      font-weight: 600;
      color: $ink;
      cursor: pointer;
    }
    .a-time {
      font-size: $fs-sm;
      color: $ink-3;
    }
  }

  .art-tags {
    display: flex;
    gap: $s-2;
    flex-wrap: wrap;
    .tag {
      padding: $s-1 $s-2;
      background: $brand-soft;
      color: $brand;
      font-size: $fs-sm;
      border-radius: $r-sm;
      font-weight: 500;
    }
  }
}

.article-body {
  margin-bottom: $s-8;

  :deep(.md-editor-preview-wrapper) {
    padding: 0;
  }
}

.article-actions {
  display: flex;
  justify-content: center;
  gap: $s-3;
  padding-top: $s-6;
  border-top: 1px solid $border;

  .act {
    display: flex;
    align-items: center;
    gap: $s-2;
    padding: $s-2 $s-5;
    border: 1px solid $border;
    background: $surface;
    border-radius: $r-md;
    font-size: $fs-md;
    color: $ink-2;
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      border-color: $brand;
      color: $brand;
    }
    &.on {
      border-color: $accent;
      color: $accent;
      background: $accent-soft;
    }
  }
}

.comments {
  margin-top: $s-8;
  padding-top: $s-6;
  border-top: 1px solid $border;

  .c-title {
    display: flex;
    align-items: center;
    gap: $s-2;
    font-size: $fs-xl;
    font-weight: 700;
    color: $ink;
    margin: 0 0 $s-5;
  }
}

.c-input {
  margin-bottom: $s-6;
}
.c-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: $s-3;
}

.c-list {
  display: flex;
  flex-direction: column;
  gap: $s-5;
}
.c-item {
  display: flex;
  gap: $s-3;
  .el-avatar {
    cursor: pointer;
    flex-shrink: 0;
  }
  .c-body {
    flex: 1;
    min-width: 0;
  }
  .c-header {
    display: flex;
    gap: $s-2;
    align-items: center;
    margin-bottom: $s-1;
    .c-author {
      font-weight: 600;
      color: $ink;
      font-size: $fs-sm;
      cursor: pointer;
    }
    .c-time {
      font-size: $fs-sm;
      color: $ink-3;
    }
    .reply-arrow {
      font-size: $fs-sm;
      color: $ink-3;
    }
  }
  .c-text {
    color: $ink-2;
    margin: 0;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
  }

  &.sub {
    margin-top: $s-4;
  }
}

.c-toolbar {
  display: flex;
  gap: $s-4;
  margin-top: $s-1;
  .tb-btn {
    display: inline-flex;
    align-items: center;
    gap: 3px;
    background: none;
    border: none;
    padding: 2px 0;
    cursor: pointer;
    font-size: $fs-sm;
    color: $ink-3;
    &:hover {
      color: $brand;
    }
    &.on {
      color: $accent;
    }
    &.danger:hover {
      color: #f56c6c;
    }
  }
}

.reply-box {
  margin-top: $s-3;
  .reply-actions {
    display: flex;
    justify-content: flex-end;
    gap: $s-2;
    margin-top: $s-2;
  }
}

.c-empty {
  text-align: center;
  color: $ink-3;
  padding: $s-8 0;
}
</style>
