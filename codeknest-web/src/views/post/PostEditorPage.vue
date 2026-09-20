<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { draftApi, metaApi, postApi } from '@/api/post'
import type { Category, Tag } from '@/api/types'

const router = useRouter()
const route = useRoute()

const editId = route.name === 'post-edit' ? Number(route.params.id) : null
const draftId = route.query.draftId ? String(route.query.draftId) : null

const title = ref('')
const summary = ref('')
const content = ref('')
const categoryId = ref<number | undefined>(undefined)
const tagInput = ref('')
const selectedTags = ref<string[]>([])

/** 本次编辑上传的临时图片 URL，发布/存草稿成功后清空，离开页面时未确认则主动删除 */
const tempImageUrls = ref<string[]>([])

const categories = ref<Category[]>([])
const availableTags = ref<Tag[]>([])
const saving = ref(false)
const publishing = ref(false)

function toggleTag(tag: string) {
  const idx = selectedTags.value.indexOf(tag)
  if (idx >= 0) selectedTags.value.splice(idx, 1)
  else if (selectedTags.value.length < 5) selectedTags.value.push(tag)
}

function addCustomTag() {
  const t = tagInput.value.trim()
  tagInput.value = ''
  if (!t) return
  if (t.length > 20) return ElMessage.warning('标签最长 20 个字符')
  if (selectedTags.value.includes(t)) return
  if (selectedTags.value.length >= 5) return ElMessage.warning('最多选择 5 个标签')
  selectedTags.value.push(t)
}

/** md-editor-v3 图片上传：上传到临时目录，发布/存草稿时后端再确认 */
async function onUploadImg(files: File[], callback: (urls: string[]) => void) {
  try {
    const urls: string[] = []
    for (const f of files) {
      const res = await postApi.uploadImage(f)
      urls.push(res.data.url)
      tempImageUrls.value.push(res.data.url)
    }
    callback(urls)
  } catch {
    ElMessage.error('图片上传失败')
  }
}

/** 删除本次编辑上传的所有临时图片 */
function cleanupTempImages() {
  if (!tempImageUrls.value.length) return
  const urls = [...tempImageUrls.value]
  tempImageUrls.value = []
  // 用 fetch keepalive 保证页面卸载时也能发出
  const token = localStorage.getItem('accessToken') || ''
  try {
    fetch('/api/posts/upload-image', {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ urls }),
      keepalive: true,
    })
  } catch {
    postApi.deleteTempImages(urls).catch(() => {})
  }
}

function validate(): boolean {
  if (!title.value.trim()) {
    ElMessage.warning('请输入标题')
    return false
  }
  if (title.value.trim().length > 200) {
    ElMessage.warning('标题最长 200 字')
    return false
  }
  if (!content.value.trim()) {
    ElMessage.warning('正文不能为空')
    return false
  }
  if (summary.value.length > 500) {
    ElMessage.warning('摘要最长 500 字')
    return false
  }
  return true
}

async function saveDraft() {
  if (!title.value.trim() && !content.value.trim()) {
    return ElMessage.warning('标题或正文至少填写一项')
  }
  saving.value = true
  try {
    await draftApi.save({
      id: draftId ?? undefined,
      title: title.value,
      content: content.value,
      summary: summary.value || undefined,
      tagNames: selectedTags.value,
      categoryId: categoryId.value,
    })
    ElMessage.success('草稿已保存')
    tempImageUrls.value = [] // 已保存，临时图已迁移到正式目录
    router.push('/me/drafts')
  } finally {
    saving.value = false
  }
}

async function publish() {
  if (!validate()) return
  publishing.value = true
  try {
    const payload = {
      title: title.value.trim(),
      summary: summary.value.trim() || undefined,
      content: content.value,
      categoryId: categoryId.value,
      tagNames: selectedTags.value,
    }
    if (editId) {
      await postApi.update(editId, payload)
      ElMessage.success('文章已更新')
      tempImageUrls.value = []
      router.push(`/posts/${editId}`)
    } else {
      const res = await postApi.create(payload)
      if (draftId) {
        try {
          await draftApi.remove(draftId)
        } catch {
          /* ignore */
        }
      }
      ElMessage.success('发布成功')
      tempImageUrls.value = []
      router.push(`/posts/${res.data.postId}`)
    }
  } finally {
    publishing.value = false
  }
}

onMounted(async () => {
  const [catRes, tagRes] = await Promise.all([metaApi.categories(), metaApi.tags()])
  categories.value = catRes.data || []
  availableTags.value = tagRes.data || []

  if (editId) {
    const res = await postApi.detail(editId)
    const p = res.data
    title.value = p.title
    summary.value = p.summary || ''
    content.value = p.content || ''
    categoryId.value = p.category?.id
    selectedTags.value = (p.tags || []).map((t) => t.name)
  } else if (draftId) {
    const res = await draftApi.detail(draftId)
    title.value = res.data.title || ''
    content.value = res.data.content || ''
    summary.value = res.data.summary || ''
    categoryId.value = res.data.categoryId ?? undefined
    selectedTags.value = res.data.tagNames || []
  }
})

// 路由离开时：未保存则清理本次上传的临时图片
onBeforeRouteLeave(() => {
  cleanupTempImages()
  return true
})

// 刷新 / 关闭标签页时清理
function onBeforeUnload() {
  cleanupTempImages()
}
window.addEventListener('beforeunload', onBeforeUnload)
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
})
</script>

<template>
  <div class="editor-page">
    <div class="editor-header">
      <el-button text @click="router.back()">← 返回</el-button>
      <span class="editor-title">{{ editId ? '编辑文章' : '写文章' }}</span>
      <div class="header-actions">
        <el-button :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button type="primary" :loading="publishing" @click="publish">
          {{ editId ? '更新文章' : '发布文章' }}
        </el-button>
      </div>
    </div>

    <div class="editor-body">
      <div class="title-input">
        <el-input v-model="title" placeholder="输入文章标题..." size="large" maxlength="200" />
      </div>

      <div class="meta-row">
        <el-select v-model="categoryId" placeholder="选择分类（可选）" clearable class="cat-select">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-input
          v-model="summary"
          placeholder="一句话摘要（可选，最多 500 字）"
          maxlength="500"
          class="summary-input"
        />
      </div>

      <div class="tag-selector">
        <span class="tag-label">标签（最多 5 个）：</span>
        <div class="tag-options">
          <button
            v-for="tag in availableTags"
            :key="tag.id"
            type="button"
            class="tag-opt"
            :class="{ active: selectedTags.includes(tag.name) }"
            @click="toggleTag(tag.name)"
          >
            {{ tag.name }}
          </button>
        </div>
        <div class="tag-custom">
          <el-input
            v-model="tagInput"
            size="small"
            placeholder="自定义标签，回车添加"
            class="tag-input"
            @keyup.enter="addCustomTag"
          />
          <el-button size="small" @click="addCustomTag">添加</el-button>
        </div>
        <div v-if="selectedTags.length" class="tag-selected">
          已选：
          <el-tag
            v-for="t in selectedTags"
            :key="t"
            closable
            type="primary"
            class="sel-tag"
            @close="toggleTag(t)"
            >{{ t }}</el-tag
          >
        </div>
      </div>

      <div class="cover-hint">封面图自动取正文第一张图片，无需单独上传</div>

      <div class="editor-main">
        <MdEditor
          v-model="content"
          placeholder="用 Markdown 写下你的技术分享..."
          :preview="true"
          :on-upload-img="onUploadImg"
          style="height: 560px"
        />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.editor-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: $s-4;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $s-3 0;
  margin-bottom: $s-4;

  .editor-title {
    font-weight: 700;
    font-size: $fs-lg;
  }
  .header-actions {
    display: flex;
    gap: $s-2;
  }
}

.editor-body {
  background: $surface;
  border: 1px solid $border;
  border-radius: $r-lg;
  padding: $s-6;
}

.title-input {
  margin-bottom: $s-4;
}

.meta-row {
  display: flex;
  gap: $s-3;
  margin-bottom: $s-4;
  .cat-select {
    width: 200px;
    flex-shrink: 0;
  }
  .summary-input {
    flex: 1;
  }
}

.tag-selector {
  margin-bottom: $s-6;

  .tag-label {
    display: block;
    font-size: $fs-sm;
    color: $ink-3;
    font-weight: 500;
    margin-bottom: $s-2;
  }

  .tag-options {
    display: flex;
    gap: $s-2;
    flex-wrap: wrap;
    margin-bottom: $s-3;

    .tag-opt {
      padding: $s-1 $s-3;
      border: 1px solid $border;
      background: $surface;
      border-radius: 999px;
      font-size: $fs-sm;
      color: $ink-2;
      cursor: pointer;
      transition: all 0.15s;

      &:hover {
        border-color: $brand;
        color: $brand;
      }
      &.active {
        background: $brand;
        border-color: $brand;
        color: #fff;
      }
    }
  }

  .tag-custom {
    display: flex;
    gap: $s-2;
    width: 280px;
    margin-bottom: $s-3;
  }

  .tag-selected {
    display: flex;
    align-items: center;
    gap: $s-2;
    flex-wrap: wrap;
    font-size: $fs-sm;
    color: $ink-3;
    .sel-tag {
      cursor: pointer;
    }
  }
}

.editor-main {
  border: 1px solid $border;
  border-radius: $r-md;
  overflow: hidden;
}

.cover-hint {
  margin-bottom: $s-4;
  font-size: $fs-sm;
  color: $ink-3;
}

@media (max-width: 768px) {
  .meta-row {
    flex-direction: column;
    .cat-select {
      width: 100%;
    }
  }
}
</style>
