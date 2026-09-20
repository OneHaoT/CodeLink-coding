<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { metaApi, noticeApi, postApi } from '@/api/post'
import type { Category, Notice, PostVO } from '@/api/types'

const router = useRouter()

interface FeedCard {
  id: number
  title: string
  summary: string
  coverImage?: string
  authorId: number
  author: string
  avatar?: string
  tags: string[]
  views: number
  likes: number
  comments: number
  hot: number
  createdAt?: string
}

function hotScore(p: PostVO): number {
  return Math.min(99, Math.round((p.viewCount || 0) / 300) + (p.likeCount || 0) * 2)
}

function mapCard(p: PostVO): FeedCard {
  return {
    id: p.id,
    title: p.title,
    summary: p.summary || extractFirstLine(p.content),
    coverImage: p.coverImage,
    authorId: p.author?.id || 0,
    author: p.author?.username || '匿名',
    avatar: p.author?.avatar,
    tags: (p.tags || []).map((t) => t.name),
    views: p.viewCount || 0,
    likes: p.likeCount || 0,
    comments: p.commentCount || 0,
    hot: hotScore(p),
    createdAt: p.publishedAt || p.createdAt,
  }
}

// 摘要缺省时从正文截取第一行
function extractFirstLine(content?: string): string {
  if (!content) return ''
  const text = content.replace(/[#*>`\-\[\]()]/g, '').trim()
  return text.slice(0, 60)
}

const feedPosts = ref<FeedCard[]>([])
const topPosts = ref<{ rank: number; title: string; hot: number; id: number }[]>([])
const notices = ref<Notice[]>([])

async function loadNotices() {
  try {
    const res = await noticeApi.list()
    notices.value = res.data || []
  } catch {
    /* 拦截器已提示 */
  }
}

const activeTab = ref<'recommend' | 'latest' | 'hot'>('recommend')
const leftCollapsed = ref(false)
const categoryList = ref<Category[]>([])
const activeCatId = ref<number | null>(null)
const page = ref(1)
const totalPages = ref(1)
const loading = ref(false)

// 分类导航统一使用 Element Plus 线性图标（按顺序取用，超出则回退 Document）
const CAT_ICONS = [
  'Cpu',
  'Monitor',
  'Coin',
  'SetUp',
  'Grid',
  'MagicStick',
  'CoffeeCup',
  'Reading',
  'Notebook',
  'Document',
]
const categories = ref<{ icon: string; label: string; id: number | null }[]>([
  { icon: 'Compass', label: '推荐', id: null },
])

async function loadCategories() {
  try {
    const res = await metaApi.categories()
    categoryList.value = res.data || []
    categories.value = [
      { icon: 'Compass', label: '推荐', id: null },
      ...(res.data || []).map((c, i) => ({
        icon: CAT_ICONS[i % CAT_ICONS.length],
        label: c.name,
        id: c.id,
      })),
    ]
  } catch {
    /* 拦截器已提示 */
  }
}

async function loadFeed(reset = false) {
  if (loading.value) return
  loading.value = true
  try {
    if (reset) {
      page.value = 1
      feedPosts.value = []
    }
    const sort = activeTab.value === 'hot' ? 'hot' : 'latest'
    const res = await postApi.list({
      page: page.value,
      size: 10,
      sort,
      categoryId: activeCatId.value ?? undefined,
    })
    const cards = (res.data.items || []).map(mapCard)
    feedPosts.value.push(...cards)
    totalPages.value = res.data.totalPages
  } finally {
    loading.value = false
  }
}

async function loadHotRank() {
  const res = await postApi.list({ page: 1, size: 5, sort: 'hot' })
  topPosts.value = (res.data.items || []).map((p, i) => ({
    rank: i + 1,
    title: p.title,
    hot: hotScore(p),
    id: p.id,
  }))
}

watch([activeTab, activeCatId], () => loadFeed(true))

function loadMore() {
  if (page.value < totalPages.value) {
    page.value += 1
    loadFeed(false)
  }
}

function selectCategory(id: number | null) {
  activeCatId.value = id
}

function formatTime(iso?: string) {
  return iso ? dayjs(iso).fromNow() : ''
}
function goPost(id: number) {
  router.push(`/posts/${id}`)
}
function goAuthor(id: number) {
  if (id) router.push(`/users/${id}`)
}

loadCategories()
loadFeed(true)
loadHotRank()
loadNotices()
</script>

<template>
  <div class="home">
    <!-- ============ 三栏布局 ============ -->
    <div class="three-col">
      <!-- 左：分类导航（可折叠） -->
      <aside class="col-left" :class="{ collapsed: leftCollapsed }">
        <div class="col-head" @click="leftCollapsed = !leftCollapsed">
          <span class="col-title">发现</span>
          <el-icon class="collapse-ic" :class="{ flipped: leftCollapsed }"><ArrowLeft /></el-icon>
        </div>
        <transition name="fade">
          <ul v-if="!leftCollapsed" class="cat-list">
            <li
              v-for="c in categories"
              :key="c.label"
              class="cat-item"
              :class="{ on: activeCatId === c.id }"
              @click="selectCategory(c.id)"
            >
              <el-icon class="cat-ic"><component :is="c.icon" /></el-icon>
              <span class="cat-label">{{ c.label }}</span>
            </li>
          </ul>
        </transition>
      </aside>

      <!-- 中：内容流 -->
      <div class="col-middle">
        <!-- Tab 切换 -->
        <div class="feed-tabs">
          <button
            v-for="t in [
              { k: 'recommend', label: '推荐' },
              { k: 'latest', label: '最新' },
              { k: 'hot', label: '热门' },
            ]"
            :key="t.k"
            class="tab-btn"
            :class="{ on: activeTab === t.k }"
            @click="activeTab = t.k as any"
          >
            {{ t.label }}
          </button>
        </div>

        <!-- 文章流 -->
        <div class="feed">
          <article
            v-for="post in feedPosts"
            :key="post.id"
            class="feed-card"
            @click="goPost(post.id)"
          >
            <div class="feed-main">
              <div class="feed-meta">
                <el-avatar :size="22" class="feed-avatar">{{
                  post.avatar || post.author[0]
                }}</el-avatar>
                <span class="feed-author" @click.stop="goAuthor(post.authorId)">{{
                  post.author
                }}</span>
                <span class="feed-dot">·</span>
                <span class="feed-time">{{ formatTime(post.createdAt) }}</span>
              </div>

              <h3 class="feed-title">{{ post.title }}</h3>
              <p class="feed-summary">{{ post.summary }}</p>

              <div class="feed-footer">
                <div class="feed-stats">
                  <span class="stat-item" title="浏览">
                    <el-icon><View /></el-icon>{{ post.views }}
                  </span>
                  <span class="stat-item" title="点赞">
                    <el-icon><ThumbsUpIcon /></el-icon>{{ post.likes }}
                  </span>
                  <span class="stat-item" title="评论">
                    <el-icon><ChatDotRound /></el-icon>{{ post.comments }}
                  </span>
                </div>
                <div class="feed-tags">
                  <span v-for="t in post.tags" :key="t" class="tag">{{ t }}</span>
                </div>
              </div>
            </div>

            <div v-if="post.coverImage" class="feed-cover">
              <img :src="post.coverImage" alt="封面" />
            </div>
          </article>
        </div>

        <div
          v-if="!feedPosts.length && !loading"
          class="load-more"
          style="color: var(--el-text-color-secondary)"
        >
          还没有文章，去发布第一篇吧
        </div>
        <div class="load-more">
          <el-button v-if="page < totalPages" :loading="loading" @click="loadMore"
            >加载更多</el-button
          >
        </div>
      </div>

      <!-- 右：推荐栏 -->
      <aside class="col-right">
        <!-- 热榜 -->
        <div class="right-card">
          <div class="right-header">
            <el-icon class="rc-ic"><Trophy /></el-icon>
            <span>热榜 Top 5</span>
          </div>
          <ul class="rank-list">
            <li v-for="p in topPosts" :key="p.rank" class="rank-item" @click="goPost(p.id)">
              <span class="rank-num" :class="{ top: p.rank <= 3 }">{{ p.rank }}</span>
              <span class="rank-title">{{ p.title }}</span>
              <span class="rank-hot">
                <el-icon><TrendCharts /></el-icon>{{ p.hot }}
              </span>
            </li>
          </ul>
        </div>

        <!-- 社区公告 -->
        <div class="right-card notice">
          <div class="right-header">
            <el-icon class="rc-ic"><Bell /></el-icon>
            <span>社区公告</span>
          </div>
          <div v-if="notices.length" class="notice-body">
            <div v-for="n in notices" :key="n.id" class="notice-item">
              <div class="notice-title">
                <el-icon><Promotion /></el-icon>
                <span>{{ n.title }}</span>
              </div>
              <div class="notice-content">{{ n.content }}</div>
            </div>
          </div>
          <div v-else class="notice-body">
            <p style="color: var(--el-text-color-placeholder)">暂无公告</p>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.home {
  padding: $s-6 0;
}

// ============ 三栏 ============
.three-col {
  max-width: 1200px;
  margin: 0 auto;
  padding: $s-6 $s-5;
  display: grid;
  grid-template-columns: 160px 1fr 260px;
  gap: $s-5;
  align-items: start;
  transition: grid-template-columns 0.25s ease;

  &:has(.col-left.collapsed) {
    grid-template-columns: 40px 1fr 260px;
  }
}

// ============ 左栏 ============
.col-left {
  position: sticky;
  top: calc(56px + #{$s-6});
  display: flex;
  flex-direction: column;
  gap: $s-2;

  &.collapsed {
    .cat-list {
      display: none;
    }
    .col-head .col-title {
      display: none;
    }
    .col-head .collapse-ic {
      transform: rotate(180deg);
    }
  }

  .col-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: $s-1 $s-2;
    cursor: pointer;
    user-select: none;

    .col-title {
      font-size: 11px;
      font-weight: 700;
      color: $ink-3;
      text-transform: uppercase;
      letter-spacing: 0.08em;
    }

    .collapse-ic {
      font-size: 14px;
      color: $ink-3;
      transition: transform 0.2s;

      &.flipped {
        transform: rotate(180deg);
      }
    }
  }
}

.cat-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.cat-item {
  display: flex;
  align-items: center;
  gap: $s-2;
  padding: $s-2 $s-3;
  font-size: $fs-md;
  color: $ink-2;
  border-radius: $r-sm;
  cursor: pointer;
  transition: all 0.15s;

  .cat-ic {
    font-size: 16px;
    flex-shrink: 0;
  }

  &:hover {
    color: $ink;
    background: $surface;
  }
  &.on {
    color: $brand;
    background: $brand-soft;
    font-weight: 600;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: all 0.15s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

// ============ 中栏 ============
.col-middle {
  min-width: 0;
}

.feed-tabs {
  display: flex;
  gap: $s-1;
  margin-bottom: $s-5;

  .tab-btn {
    padding: $s-2 $s-4;
    border: none;
    background: transparent;
    font-size: $fs-lg;
    font-weight: 500;
    color: $ink-3;
    cursor: pointer;
    border-radius: $r-sm;
    transition: all 0.15s;

    &:hover {
      color: $ink;
    }
    &.on {
      color: $ink;
      font-weight: 700;

      &::after {
        content: '';
        display: block;
        width: 20px;
        height: 3px;
        background: $brand;
        border-radius: 2px;
        margin: 2px auto 0;
      }
    }
  }
}

// --- Hero 大卡 ---
.hero-card {
  position: relative;
  background: $surface;
  border-radius: $r-lg;
  overflow: hidden;
  margin-bottom: $s-6;
  cursor: pointer;
  transition: all 0.25s;
  box-shadow: $sh-2;
  border: 1px solid $border;

  &:hover {
    box-shadow: $sh-3;
    transform: translateY(-2px);
  }

  .hero-gradient {
    height: 140px;
    background: linear-gradient(135deg, $brand 0%, darken($brand, 20%) 50%, #1e1b4b 100%);
    position: relative;

    &::after {
      content: '';
      position: absolute;
      inset: 0;
      background:
        radial-gradient(ellipse at 80% 30%, rgba(255, 255, 255, 0.15) 0%, transparent 50%),
        radial-gradient(ellipse at 20% 80%, rgba(245, 158, 11, 0.2) 0%, transparent 50%);
    }

    .hero-badge {
      position: absolute;
      top: $s-4;
      left: $s-4;
      padding: $s-1 $s-3;
      background: rgba(255, 255, 255, 0.15);
      backdrop-filter: blur(8px);
      border-radius: $r-full;
      font-size: $fs-sm;
      font-weight: 600;
      color: #fff;
      border: 1px solid rgba(255, 255, 255, 0.2);
    }
  }

  .hero-body {
    padding: $s-5 $s-6 $s-6;
  }

  .hero-meta {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: $s-3;

    .hero-author {
      display: flex;
      align-items: center;
      gap: $s-2;
      font-size: $fs-sm;
      color: $ink-2;

      .h-avatar {
        width: 28px;
        height: 28px;
        background: $brand-soft;
        color: $brand;
        border-radius: $r-full;
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: 700;
        font-size: $fs-sm;
      }
    }
    .hero-time {
      font-size: $fs-sm;
      color: $ink-3;
    }
  }

  .hero-title {
    font-family: $font-display;
    font-size: 26px;
    font-weight: 700;
    line-height: 1.3;
    margin: 0 0 $s-3;
    color: $ink;
    letter-spacing: -0.01em;
  }

  .hero-summary {
    font-size: $fs-base;
    color: $ink-2;
    line-height: 1.6;
    margin: 0 0 $s-4;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .hero-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .hero-tags {
      display: flex;
      gap: $s-2;

      .tag {
        padding: 3px $s-2;
        background: $brand-soft;
        color: $brand;
        font-size: $fs-sm;
        border-radius: $r-sm;
        font-weight: 500;
      }
    }

    .hero-stats {
      display: flex;
      gap: $s-4;
      font-size: $fs-sm;
      color: $ink-3;

      .stat-hot {
        font-weight: 700;
        color: $accent;
      }
    }
  }

  // 侧边热度条
  .hero-progress {
    position: absolute;
    right: 0;
    top: 0;
    bottom: 0;
    width: 4px;
    background: rgba($brand, 0.1);

    .progress-bar {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      background: linear-gradient(to top, $brand, $accent);
      border-radius: 2px;
      transition: height 0.6s ease;
    }
  }
}

// --- 文章流 ---
.feed {
  display: flex;
  flex-direction: column;
  gap: $s-3;
}

.feed-card {
  position: relative;
  display: flex;
  gap: $s-4;
  padding: $s-4 $s-5;
  background: $surface;
  border: 1px solid $border;
  border-radius: $r-md;
  cursor: pointer;
  transition: all 0.2s;
  overflow: hidden;

  &:hover {
    border-color: $brand;
    box-shadow: $sh-2;
    transform: translateX(2px);

    .feed-cover img {
      transform: scale(1.05);
    }
  }

  .feed-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }

  .feed-meta {
    display: flex;
    align-items: center;
    gap: $s-2;
    margin-bottom: $s-2;

    .feed-author {
      font-size: $fs-sm;
      color: $ink-2;
      font-weight: 500;
      cursor: pointer;
    }
    .feed-dot {
      color: $ink-4;
    }
    .feed-time {
      font-size: $fs-sm;
      color: $ink-3;
    }
  }

  .feed-title {
    font-size: $fs-xl;
    font-weight: 700;
    color: $ink;
    margin: 0 0 $s-2;
    line-height: 1.35;
  }

  .feed-summary {
    font-size: $fs-md;
    color: $ink-2;
    margin: 0 0 $s-3;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    line-height: 1.6;
  }

  .feed-footer {
    margin-top: auto;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $s-3;
  }

  .feed-stats {
    display: flex;
    gap: $s-4;
    font-size: 12px;
    color: $ink-3;

    .stat-item {
      display: inline-flex;
      align-items: center;
      gap: 3px;
    }
  }

  .feed-tags {
    display: flex;
    gap: $s-2;
    flex-shrink: 0;

    .tag {
      padding: 2px $s-2;
      background: $surface-alt;
      color: $ink-2;
      font-size: 12px;
      border-radius: $r-sm;
    }
  }

  // 右侧封面
  .feed-cover {
    flex-shrink: 0;
    width: 120px;
    height: 90px;
    border-radius: $r-sm;
    overflow: hidden;
    background: $surface-alt;
    align-self: center;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: transform 0.3s ease;
      display: block;
    }

    .cover-placeholder {
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: $ink-4;
      background: linear-gradient(135deg, $surface-alt 0%, lighten($surface-alt, 4%) 100%);
    }
  }
}

.load-more {
  text-align: center;
  margin-top: $s-6;
  padding-bottom: $s-8;
}

// ============ 右栏 ============
.col-right {
  position: sticky;
  top: $s-6;
  display: flex;
  flex-direction: column;
  gap: $s-4;
}

.right-card {
  background: $surface;
  border: 1px solid $border;
  border-radius: $r-md;
  overflow: hidden;

  .right-header {
    display: flex;
    align-items: center;
    gap: $s-2;
    padding: $s-3 $s-4;
    font-size: $fs-md;
    font-weight: 700;
    color: $ink;
    border-bottom: 1px solid $border;

    .rc-ic {
      font-size: 16px;
      color: $brand;
    }
  }
}

.rank-list {
  list-style: none;
  padding: $s-2 0;
  margin: 0;

  .rank-item {
    display: flex;
    align-items: center;
    gap: $s-3;
    padding: $s-2 $s-4;
    font-size: $fs-sm;
    cursor: pointer;
    transition: background 0.15s;

    &:hover {
      background: $surface-alt;
    }

    .rank-num {
      width: 20px;
      height: 20px;
      background: $surface-alt;
      color: $ink-3;
      border-radius: $r-sm;
      font-family: $font-mono;
      font-size: 11px;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;

      &.top {
        background: $accent;
        color: #fff;
      }
    }

    .rank-title {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      color: $ink;
    }

    .rank-hot {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      font-family: $font-mono;
      font-size: $fs-xs;
      color: $accent;
      flex-shrink: 0;
    }
  }
}

.author-list {
  list-style: none;
  padding: $s-2 0;
  margin: 0;

  .author-item {
    display: flex;
    align-items: center;
    gap: $s-3;
    padding: $s-2 $s-4;

    .a-avatar {
      width: 32px;
      height: 32px;
      border-radius: $r-full;
      background: $brand;
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: $fs-md;
      flex-shrink: 0;
    }

    .a-info {
      flex: 1;
      min-width: 0;

      .a-name {
        font-size: $fs-md;
        font-weight: 600;
        color: $ink;
      }

      .a-meta {
        font-size: 11px;
        color: $ink-3;
      }
    }

    .follow-btn {
      padding: 0 $s-2;
    }
  }
}

.notice {
  .notice-body {
    padding: $s-3 $s-4;
    font-size: $fs-sm;
    color: $ink-2;
    line-height: 1.6;

    p {
      margin: 0;
    }
  }

  .notice-item {
    padding: $s-2 0;
    border-bottom: 1px dashed $border;

    &:last-child {
      border-bottom: none;
    }

    .notice-title {
      display: flex;
      align-items: center;
      gap: $s-1;
      font-weight: 600;
      color: $ink;
      margin-bottom: 2px;
      line-height: 1.4;

      .el-icon {
        color: $ink-3;
        flex-shrink: 0;
      }
    }

    .notice-content {
      color: $ink-3;
      line-height: 1.5;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
  }
}

// ============ 响应式 ============
@media (max-width: 1024px) {
  .three-col {
    grid-template-columns: 120px 1fr 220px;
    gap: $s-4;

    &:has(.col-left.collapsed) {
      grid-template-columns: 32px 1fr 220px;
    }
  }
}

@media (max-width: 768px) {
  .three-col {
    grid-template-columns: 1fr;
    gap: $s-4;
    padding: $s-4;

    &:has(.col-left.collapsed) {
      grid-template-columns: 1fr;
    }
  }

  .col-left {
    position: static;
    flex-direction: row;
    align-items: center;
    flex-wrap: wrap;

    .col-head {
      display: none;
    }
    .cat-list {
      flex-direction: row;
      overflow-x: auto;
      flex-wrap: nowrap;
      gap: $s-2;
      padding-bottom: $s-2;
    }
    .cat-item {
      flex-shrink: 0;
      padding: $s-1 $s-3;
      font-size: $fs-sm;
    }
  }

  .col-right {
    position: static;
  }

  .hero-card {
    .hero-gradient {
      height: 100px;
    }
    .hero-title {
      font-size: 20px;
    }
    .hero-progress {
      display: none;
    }
  }

  .feed-card {
    padding: $s-3 $s-4;

    .feed-cover {
      width: 90px;
      height: 68px;
    }
  }
}
</style>
