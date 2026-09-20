<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { draftApi } from '@/api/post'
import type { DraftVO } from '@/api/types'

const router = useRouter()
const drafts = ref<DraftVO[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await draftApi.list()
    drafts.value = res.data || []
  } finally {
    loading.value = false
  }
}

function edit(d: DraftVO) {
  if (d.postId) router.push(`/posts/${d.postId}/edit`)
  else router.push({ path: '/posts/create', query: { draftId: d.id } })
}

async function remove(d: DraftVO) {
  await ElMessageBox.confirm('确定删除这个草稿吗？', '提示', { type: 'warning' })
  await draftApi.remove(d.id)
  ElMessage.success('已删除')
  await load()
}

function preview(content?: string) {
  return content ? content.replace(/[#>*`\-]/g, '').slice(0, 120) : '（无正文）'
}
function time(t?: string) {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : ''
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="draft-page">
    <h1 class="page-title">
      <el-icon class="pt-ic"><Memo /></el-icon>
      <span>草稿箱</span>
    </h1>

    <article v-for="d in drafts" :key="d.id" class="draft-card">
      <div class="dc-main" @click="edit(d)">
        <h2 class="dc-title">{{ d.title || '未命名草稿' }}</h2>
        <p class="dc-preview">{{ preview(d.content) }}</p>
        <span class="dc-time">最近更新：{{ time(d.updatedAt) }}</span>
      </div>
      <div class="dc-ops">
        <el-button size="small" type="primary" @click="edit(d)">继续编辑</el-button>
        <el-button size="small" type="danger" plain @click="remove(d)">删除</el-button>
      </div>
    </article>

    <el-empty v-if="!loading && !drafts.length" description="草稿箱是空的" />
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.draft-page {
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

.draft-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $s-4;
  padding: $s-5;
  margin-bottom: $s-3;
  background: $surface;
  border: 1px solid $border;
  border-radius: $r-md;
}
.dc-main {
  flex: 1;
  cursor: pointer;
  min-width: 0;
}
.dc-title {
  font-size: $fs-lg;
  font-weight: 700;
  margin: 0 0 $s-2;
}
.dc-preview {
  color: $ink-3;
  font-size: $fs-sm;
  margin: 0 0 $s-2;
  line-height: 1.6;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.dc-time {
  font-size: $fs-sm;
  color: $ink-3;
}
.dc-ops {
  display: flex;
  gap: $s-2;
  flex-shrink: 0;
}
</style>
