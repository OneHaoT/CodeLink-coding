<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { interactionApi } from '@/api/post'
import type { PostVO } from '@/api/types'

const router = useRouter()
const posts = ref<PostVO[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const res = await interactionApi.favorites()
    posts.value = res.data || []
  } finally {
    loading.value = false
  }
})

function time(t?: string) {
  return t ? dayjs(t).fromNow() : ''
}
</script>

<template>
  <div class="fav-page">
    <h1 class="page-title">
      <el-icon class="pt-ic"><Star /></el-icon>
      <span>我的收藏</span>
    </h1>
    <div v-loading="loading">
      <article
        v-for="p in posts"
        :key="p.id"
        class="fav-card"
        @click="router.push(`/posts/${p.id}`)"
      >
        <div class="fc-main">
          <div class="fc-meta">
            <span class="fc-author">{{ p.author?.username || '匿名' }}</span>
            <span class="fc-dot">·</span>
            <span>{{ time(p.publishedAt || p.createdAt) }}</span>
          </div>
          <h2 class="fc-title">{{ p.title }}</h2>
          <p class="fc-summary">{{ p.summary }}</p>
          <div class="fc-tags">
            <span v-for="t in p.tags || []" :key="t.id" class="tag">{{ t.name }}</span>
          </div>
        </div>
        <div class="fc-stats">
          <span><el-icon><View /></el-icon>{{ p.viewCount }}</span>
          <span><el-icon><ThumbsUpIcon /></el-icon>{{ p.likeCount }}</span>
          <span><el-icon><ChatDotRound /></el-icon>{{ p.commentCount }}</span>
        </div>
      </article>

      <el-empty v-if="!loading && !posts.length" description="还没有收藏任何文章" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.fav-page {
  max-width: 800px;
  margin: 0 auto;
  padding: $s-6 $s-4 $s-10;
}
.page-title {
  display: flex;
  align-items: center;
  gap: $s-2;
  font-size: $fs-3xl;
  font-weight: 800;
  margin: 0 0 $s-6;

  .pt-ic {
    color: $brand;
  }
}

.fav-card {
  display: flex;
  justify-content: space-between;
  gap: $s-4;
  padding: $s-5;
  margin-bottom: $s-3;
  background: $surface;
  border: 1px solid $border;
  border-radius: $r-md;
  cursor: pointer;
  transition: all 0.15s;
  &:hover {
    border-color: $brand;
    transform: translateY(-1px);
  }
}

.fc-meta {
  font-size: $fs-sm;
  color: $ink-3;
  margin-bottom: $s-2;
  .fc-author {
    color: $ink-2;
    font-weight: 600;
  }
  .fc-dot {
    margin: 0 $s-1;
  }
}
.fc-title {
  font-size: $fs-xl;
  font-weight: 700;
  margin: 0 0 $s-2;
}
.fc-summary {
  color: $ink-3;
  margin: 0 0 $s-3;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.fc-tags {
  display: flex;
  gap: $s-2;
  flex-wrap: wrap;
  .tag {
    padding: 2px $s-2;
    background: $brand-soft;
    color: $brand;
    font-size: $fs-sm;
    border-radius: $r-sm;
  }
}
.fc-stats {
  display: flex;
  flex-direction: column;
  gap: $s-1;
  font-size: $fs-sm;
  color: $ink-3;
  white-space: nowrap;

  span {
    display: inline-flex;
    align-items: center;
    gap: 3px;
  }
}
</style>
