<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { metaApi, postApi } from '@/api/post'
import type { PostVO, Tag } from '@/api/types'

const router = useRouter()
const route = useRoute()
const activeTag = ref('')
const searchQ = computed(() => (route.query.q as string)?.trim() || '')

interface Card {
  id: number
  title: string
  summary: string
  authorId: number
  author: string
  tags: string[]
  hot: number
  createdAt?: string
}

const allPosts = ref<Card[]>([])
const tagList = ref<Tag[]>([])
const page = ref(1)
const totalPages = ref(1)
const loading = ref(false)

function hotScore(p: PostVO) {
  return Math.min(99, Math.round((p.viewCount || 0) / 300) + (p.likeCount || 0) * 2)
}

const posts = computed(() => allPosts.value)

async function loadTags() {
  try {
    const res = await metaApi.tags()
    tagList.value = res.data || []
  } catch {
    /* ignore */
  }
}

async function load(reset = true) {
  if (loading.value) return
  loading.value = true
  try {
    if (reset) {
      page.value = 1
      allPosts.value = []
    }
    const res = await postApi.list({
      page: page.value,
      size: 20,
      q: searchQ.value || undefined,
      tag: activeTag.value || undefined,
    })
    const cards: Card[] = (res.data.items || []).map((p) => ({
      id: p.id,
      title: p.title,
      summary: p.summary || '',
      authorId: p.author?.id || 0,
      author: p.author?.username || '匿名',
      tags: (p.tags || []).map((t) => t.name),
      hot: hotScore(p),
      createdAt: p.publishedAt || p.createdAt,
    }))
    allPosts.value.push(...cards)
    totalPages.value = res.data.totalPages
  } finally {
    loading.value = false
  }
}

function loadMore() {
  if (page.value < totalPages.value) {
    page.value += 1
    load(false)
  }
}

watch([searchQ, activeTag], () => load(true))

function formatTime(iso?: string) {
  return iso ? dayjs(iso).fromNow() : ''
}
function goPost(id: number) {
  router.push(`/posts/${id}`)
}
function clearSearch() {
  router.replace({ path: '/posts' })
}

loadTags()
load(true)
</script>

<template>
  <div class="list-page">
    <div class="page-head">
      <div class="ph-left">
        <h1 class="ph-title">
          <el-icon class="ph-ic"><Search v-if="searchQ" /><Files v-else /></el-icon>
          <span>{{ searchQ ? '搜索结果' : '全部文章' }}</span>
        </h1>
        <p class="ph-sub">
          <template v-if="searchQ">
            「{{ searchQ }}」· {{ posts.length }} 条结果
            <button class="clear-q" @click="clearSearch">清除搜索</button>
          </template>
          <template v-else>{{ posts.length }} 篇 · 持续更新中</template>
        </p>
      </div>
      <el-button type="primary" @click="router.push('/posts/create')">
        <el-icon><EditPen /></el-icon>写文章
      </el-button>
    </div>

    <div class="tag-bar">
      <button class="t-chip" :class="{ on: !activeTag }" @click="activeTag = ''">全部</button>
      <button
        v-for="t in tagList"
        :key="t.id"
        class="t-chip"
        :class="{ on: activeTag === t.name }"
        @click="activeTag = t.name"
      >
        {{ t.name }}
      </button>
    </div>

    <div class="p-list">
      <article v-for="post in posts" :key="post.id" class="p-card" @click="goPost(post.id)">
        <div class="p-main">
          <div class="p-meta">
            <el-avatar :size="24">{{ post.author[0] }}</el-avatar>
            <span class="p-author">{{ post.author }}</span>
            <span class="p-dot">·</span>
            <span class="p-time">{{ formatTime(post.createdAt) }}</span>
          </div>
          <h2 class="p-title">{{ post.title }}</h2>
          <p class="p-summary">{{ post.summary }}</p>
          <div class="p-tags">
            <span v-for="t in post.tags" :key="t" class="tag">{{ t }}</span>
          </div>
        </div>
        <div class="p-hot">
          <div class="hot-bar" :style="{ height: post.hot + '%' }"></div>
          <span class="hot-val">{{ post.hot }}</span>
        </div>
      </article>

      <!-- 空状态 -->
      <div v-if="posts.length === 0 && !loading" class="empty-hint">
        <el-icon class="empty-ic"><Search /></el-icon>
        <div class="empty-title">{{ searchQ ? '没有找到匹配的文章' : '还没有文章' }}</div>
        <div class="empty-desc">
          {{ searchQ ? '换个关键词试试，或者清除搜索浏览全部' : '成为第一个发布文章的人吧' }}
        </div>
        <el-button v-if="searchQ" size="small" @click="clearSearch">清除搜索</el-button>
      </div>

      <div v-if="page < totalPages" class="more-wrap">
        <el-button :loading="loading" @click="loadMore">加载更多</el-button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.list-page {
  max-width: 800px;
  margin: 0 auto;
  padding: $s-6 $s-4 $s-10;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: $s-6;

  .ph-title {
    display: flex;
    align-items: center;
    gap: $s-2;
    font-size: $fs-4xl;
    font-weight: 800;
    margin: 0;
    letter-spacing: -0.02em;

    .ph-ic {
      color: $brand;
      flex-shrink: 0;
    }
  }
  .ph-sub {
    font-size: $fs-base;
    color: $ink-3;
    margin: $s-1 0 0;
  }

  .clear-q {
    background: none;
    border: none;
    cursor: pointer;
    color: $brand;
    font-size: $fs-sm;
    margin-left: $s-3;
    padding: 0;

    &:hover {
      text-decoration: underline;
    }
  }
}

.tag-bar {
  display: flex;
  gap: $s-2;
  flex-wrap: wrap;
  margin-bottom: $s-6;

  .t-chip {
    padding: $s-1 $s-3;
    border: 1px solid $border;
    background: $surface;
    border-radius: $r-full;
    font-size: $fs-sm;
    color: $ink-2;
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      border-color: $brand;
      color: $brand;
    }
    &.on {
      background: $brand;
      border-color: $brand;
      color: #fff;
    }
  }
}

.p-list {
  display: flex;
  flex-direction: column;
  gap: $s-3;
}

.empty-hint {
  padding: $s-12 $s-4;
  text-align: center;
  background: $surface;
  border: 1px dashed $border;
  border-radius: $r-md;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $s-3;

  .empty-ic {
    font-size: 40px;
    color: $ink-4;
  }
  .empty-title {
    font-size: $fs-lg;
    font-weight: 600;
    color: $ink;
  }
  .empty-desc {
    font-size: $fs-sm;
    color: $ink-3;
  }
}

.p-card {
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
  }

  .p-main {
    flex: 1;
    min-width: 0;
  }

  .p-meta {
    display: flex;
    align-items: center;
    gap: $s-2;
    margin-bottom: $s-2;

    .p-author {
      font-size: $fs-sm;
      color: $ink-2;
      font-weight: 500;
    }
    .p-dot {
      color: $ink-4;
    }
    .p-time {
      font-size: $fs-sm;
      color: $ink-3;
    }
  }

  .p-title {
    font-size: $fs-xl;
    font-weight: 700;
    margin: 0 0 $s-2;
    line-height: 1.35;
    color: $ink;
  }
  .p-summary {
    font-size: $fs-md;
    color: $ink-2;
    margin: 0 0 $s-3;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    line-height: 1.6;
  }
  .p-tags {
    display: flex;
    gap: $s-2;
    .tag {
      padding: 2px $s-2;
      background: $surface-alt;
      color: $ink-2;
      font-size: 12px;
      border-radius: $r-sm;
    }
  }

  .p-hot {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $s-1;
    width: 20px;
    flex-shrink: 0;
    padding-top: $s-1;

    .hot-bar {
      width: 4px;
      background: linear-gradient(to top, $brand, $accent);
      border-radius: 2px;
      min-height: 4px;
    }
    .hot-val {
      font-family: $font-mono;
      font-size: 11px;
      font-weight: 700;
      color: $accent;
    }
  }
}
</style>
