<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchApi } from '@/api/search'
import type { SearchResultItem } from '@/api/types'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const results = ref<SearchResultItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const sort = ref<'relevance' | 'time' | 'views'>('relevance')
const searching = ref(false)
const searched = ref(false)
const error = ref('')

const SORTS: { key: 'relevance' | 'time' | 'views'; label: string }[] = [
  { key: 'relevance', label: '相关度' },
  { key: 'time', label: '最新' },
  { key: 'views', label: '最多浏览' },
]

/** 后端热词（Redis ZSET 累积，阶段 5 接口）；获取失败或暂无数据时回退默认词，保证展示区不空 */
const DEFAULT_HOT_WORDS = ['Spring Boot', 'Vue 3', 'Java 21', '微服务', 'AI']
const hotWords = ref<string[]>([...DEFAULT_HOT_WORDS])

async function loadHotwords() {
  try {
    const res = await searchApi.hotwords()
    if (res.data?.length) hotWords.value = res.data
  } catch {
    /* 静默回退默认词 */
  }
}

async function doSearch(resetPage = true) {
  const kw = keyword.value.trim()
  if (!kw) return
  if (resetPage) page.value = 1
  searching.value = true
  error.value = ''
  try {
    const res = await searchApi.posts({ q: kw, page: page.value, size, sort: sort.value })
    results.value = res.data?.items || []
    total.value = res.data?.total || 0
    searched.value = true
    syncQuery(kw)
  } catch {
    results.value = []
    total.value = 0
    searched.value = true
    error.value = '搜索服务暂时不可用，请稍后重试'
  } finally {
    searching.value = false
  }
}

function syncQuery(kw: string) {
  const next = { q: kw, ...(sort.value === 'relevance' ? {} : { sort: sort.value }) }
  if (route.query.q !== next.q || route.query.sort !== next.sort) {
    router.replace({ path: '/search', query: next })
  }
}

function pickWord(word: string) {
  keyword.value = word
  doSearch()
}

function changeSort(key: 'relevance' | 'time' | 'views') {
  if (sort.value === key) return
  sort.value = key
  if (searched.value) doSearch()
}

function onPageChange(p: number) {
  page.value = p
  doSearch(false)
}

function goPost(id: number) {
  router.push(`/posts/${id}`)
}

/** 后端返回的高亮为 <em>…</em>，其余内容做转义，避免 XSS */
function highlight(text?: string) {
  if (!text) return ''
  const escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  if (escaped.includes('&lt;em&gt;')) {
    return escaped.replace(/&lt;em&gt;/g, '<em>').replace(/&lt;\/em&gt;/g, '</em>')
  }
  const kw = keyword.value.trim()
  if (!kw) return escaped
  const reg = new RegExp(`(${kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
  return escaped.replace(reg, '<em>$1</em>')
}

onMounted(() => {
  loadHotwords()
  keyword.value = (route.query.q as string) || ''
  const s = route.query.sort as string
  if (s === 'time' || s === 'views') sort.value = s
  if (keyword.value) doSearch()
})

// 顶部搜索框再次跳转 /search?q= 时（组件未重建），同步关键词并重新检索
watch(
  () => route.query.q,
  (q) => {
    const kw = (q as string) || ''
    if (kw === keyword.value) return
    keyword.value = kw
    if (kw) doSearch()
  },
)
</script>

<template>
  <div class="s-page">
    <div class="s-hero">
      <el-icon class="s-ic"><Search /></el-icon>
      <h1 class="s-title">搜索文章</h1>
      <div class="s-bar">
        <input
          v-model="keyword"
          type="text"
          placeholder="输入关键词..."
          @keyup.enter="doSearch()"
        />
        <button @click="doSearch()">搜索</button>
      </div>
      <div class="s-hot">
        <span class="hot-label">热门:</span>
        <button v-for="kw in hotWords" :key="kw" class="hot-kw" @click="pickWord(kw)">
          {{ kw }}
        </button>
      </div>
    </div>

    <div class="s-body">
      <div v-if="searched" class="s-tools">
        <div class="sort-tabs">
          <button
            v-for="s in SORTS"
            :key="s.key"
            class="sort-tab"
            :class="{ on: sort === s.key }"
            @click="changeSort(s.key)"
          >
            {{ s.label }}
          </button>
        </div>
        <span v-if="!error" class="result-meta">找到 {{ total }} 个结果</span>
      </div>

      <el-alert
        v-if="error"
        class="s-error"
        type="error"
        :title="error"
        :closable="false"
        show-icon
      />

      <div v-loading="searching" class="result-list">
        <div v-for="item in results" :key="item.id" class="result-card" @click="goPost(item.id)">
          <h3 class="r-title" v-html="highlight(item.title)"></h3>
          <p class="r-summary" v-html="highlight(item.summary)"></p>
          <div class="r-meta">
            <span class="author">{{ item.author?.username || '匿名' }}</span>
            <template v-if="item.tags?.length">
              <span class="sep">·</span>
              <span v-for="t in item.tags" :key="t" class="tag">{{ t }}</span>
            </template>
            <span class="sep">·</span>
            <span class="views">
              <el-icon><View /></el-icon>{{ item.viewCount ?? 0 }}
            </span>
          </div>
        </div>
      </div>

      <el-empty
        v-if="!searching && searched && !error && !results.length"
        description="没有找到相关结果，试试其他关键词"
      />

      <div v-if="total > size" class="s-pager">
        <el-pagination
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          background
          @current-change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.s-page {
  max-width: 800px;
  margin: 0 auto;
  padding: $s-8 $s-4 $s-12;
}

.s-hero {
  text-align: center;
  margin-bottom: $s-8;

  .s-ic {
    font-size: 44px;
    color: $brand;
    margin-bottom: $s-2;
  }
  .s-title {
    font-size: $fs-4xl;
    font-weight: 800;
    margin: 0 0 $s-5;
    letter-spacing: -0.02em;
  }

  .s-bar {
    display: flex;
    max-width: 500px;
    margin: 0 auto $s-4;
    background: $surface;
    border: 2px solid $border;
    border-radius: $r-md;
    overflow: hidden;
    transition: border-color 0.15s;

    &:focus-within {
      border-color: $brand;
    }

    input {
      flex: 1;
      border: none;
      outline: none;
      padding: $s-3 $s-4;
      font-size: $fs-base;
      font-family: inherit;
      background: transparent;
    }

    button {
      padding: 0 $s-6;
      border: none;
      background: $brand;
      color: #fff;
      font-weight: 600;
      font-size: $fs-md;
      cursor: pointer;

      &:hover {
        background: darken($brand, 8%);
      }
    }
  }

  .s-hot {
    display: flex;
    justify-content: center;
    gap: $s-2;
    flex-wrap: wrap;
    .hot-label {
      font-size: $fs-sm;
      color: $ink-3;
    }
    .hot-kw {
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
        background: $brand-soft;
      }
    }
  }
}

.s-body {
  .s-tools {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $s-3;
    margin-bottom: $s-4;
    flex-wrap: wrap;
  }

  .result-meta {
    font-size: $fs-sm;
    color: $ink-3;
  }

  .s-error {
    margin-bottom: $s-4;
  }

  .s-pager {
    display: flex;
    justify-content: center;
    margin-top: $s-6;
  }
}

.sort-tabs {
  display: flex;
  gap: $s-1;
  background: $surface-alt;
  padding: 3px;
  border-radius: $r-md;

  .sort-tab {
    padding: $s-1 $s-4;
    border: none;
    background: transparent;
    border-radius: $r-sm;
    font-size: $fs-sm;
    font-family: inherit;
    color: $ink-2;
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      color: $brand;
    }
    &.on {
      background: $surface;
      color: $brand;
      font-weight: 600;
      box-shadow: $sh-1;
    }
  }
}

.result-list {
  display: flex;
  flex-direction: column;
  gap: $s-3;
  min-height: 60px;
}

.result-card {
  background: $surface;
  border: 1px solid $border;
  border-radius: $r-md;
  padding: $s-4 $s-5;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: $brand;
    box-shadow: $sh-2;
    transform: translateX(2px);
  }

  .r-title {
    font-size: $fs-xl;
    font-weight: 700;
    margin: 0 0 $s-2;
    color: $brand;
    line-height: 1.35;

    :deep(em) {
      background: $accent-soft;
      color: $accent;
      padding: 0 2px;
      border-radius: 2px;
      font-style: normal;
    }
  }

  .r-summary {
    font-size: $fs-base;
    color: $ink-2;
    margin: 0 0 $s-3;
    line-height: 1.6;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;

    :deep(em) {
      background: $accent-soft;
      color: $accent;
      padding: 0 2px;
      border-radius: 2px;
      font-style: normal;
    }
  }

  .r-meta {
    font-size: $fs-sm;
    color: $ink-3;
    display: flex;
    align-items: center;
    gap: $s-2;
    flex-wrap: wrap;
    .sep {
      color: $border-strong;
    }
    .tag {
      padding: 2px $s-2;
      background: $surface-alt;
      color: $ink-2;
      font-size: $fs-xs;
      border-radius: $r-sm;
    }
    .views {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      font-family: $font-mono;
    }
  }
}
</style>
