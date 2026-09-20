<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createCategory,
  createTag,
  deleteCategory,
  deleteTag,
  getCategories,
  getTags,
  updateCategory,
  updateTag,
} from '@/api/meta'
import type { Category, Tag } from '@/api/types'

const activeTab = ref('tags')
const loading = ref(false)
const tags = ref<Tag[]>([])
const categories = ref<Category[]>([])

// 标签前端分页
const tagPage = ref(1)
const tagSize = ref(10)
const pagedTags = computed(() =>
  tags.value.slice((tagPage.value - 1) * tagSize.value, tagPage.value * tagSize.value),
)

// ---------- 标签弹窗 ----------
const tagDialogVisible = ref(false)
const tagEditing = ref<Tag | null>(null)
const tagFormRef = ref<FormInstance>()
const tagForm = reactive({ name: '', slug: '' })
const tagRules: FormRules = {
  name: [
    { required: true, message: '请输入标签名', trigger: 'blur' },
    { max: 32, message: '最长 32 字符', trigger: 'blur' },
  ],
}

function openCreateTag() {
  tagEditing.value = null
  tagForm.name = ''
  tagForm.slug = ''
  tagDialogVisible.value = true
}

function openEditTag(row: Tag) {
  tagEditing.value = row
  tagForm.name = row.name
  tagForm.slug = row.slug
  tagDialogVisible.value = true
}

async function submitTag() {
  await tagFormRef.value?.validate()
  const payload = {
    name: tagForm.name.trim(),
    slug: tagForm.slug.trim() || undefined,
  }
  if (tagEditing.value) {
    await updateTag(tagEditing.value.id, payload)
    ElMessage.success('已更新')
  } else {
    await createTag(payload)
    ElMessage.success('已创建')
  }
  tagDialogVisible.value = false
  loadAll()
}

async function handleDeleteTag(row: Tag) {
  await ElMessageBox.confirm(
    `确定删除标签「${row.name}」？相关文章将解除该标签关联。`,
    '删除确认',
    {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    },
  )
  await deleteTag(row.id)
  ElMessage.success('已删除')
  loadAll()
}

// ---------- 分类弹窗 ----------
const catDialogVisible = ref(false)
const catEditing = ref<Category | null>(null)
const catFormRef = ref<FormInstance>()
const catForm = reactive({ name: '', slug: '', description: '', sortOrder: 0 })
const catRules: FormRules = {
  name: [
    { required: true, message: '请输入分类名', trigger: 'blur' },
    { max: 32, message: '最长 32 字符', trigger: 'blur' },
  ],
}

function openCreateCategory() {
  catEditing.value = null
  catForm.name = ''
  catForm.slug = ''
  catForm.description = ''
  catForm.sortOrder = 0
  catDialogVisible.value = true
}

function openEditCategory(row: Category) {
  catEditing.value = row
  catForm.name = row.name
  catForm.slug = row.slug
  catForm.description = row.description || ''
  catForm.sortOrder = row.sortOrder
  catDialogVisible.value = true
}

async function submitCategory() {
  await catFormRef.value?.validate()
  const payload = {
    name: catForm.name.trim(),
    slug: catForm.slug.trim() || undefined,
    description: catForm.description.trim() || undefined,
    sortOrder: catForm.sortOrder,
  }
  if (catEditing.value) {
    await updateCategory(catEditing.value.id, payload)
    ElMessage.success('已更新')
  } else {
    await createCategory(payload)
    ElMessage.success('已创建')
  }
  catDialogVisible.value = false
  loadAll()
}

async function handleDeleteCategory(row: Category) {
  await ElMessageBox.confirm(
    `确定删除分类「${row.name}」？该分类下文章将变为未分类。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await deleteCategory(row.id)
  ElMessage.success('已删除')
  loadAll()
}

async function loadAll() {
  loading.value = true
  try {
    const [t, c] = await Promise.all([getTags(), getCategories()])
    tags.value = t.data
    categories.value = c.data
  } finally {
    loading.value = false
  }
}

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm')
}

onMounted(loadAll)
</script>

<template>
  <el-card shadow="never">
    <el-tabs v-model="activeTab">
      <!-- 标签 -->
      <el-tab-pane label="标签管理" name="tags">
        <div class="toolbar">
          <el-button type="primary" :icon="'Plus'" @click="openCreateTag">新增标签</el-button>
        </div>
        <el-table v-loading="loading" :data="pagedTags" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" min-width="140" />
          <el-table-column prop="slug" label="Slug" min-width="160" show-overflow-tooltip />
          <el-table-column prop="postCount" label="文章数" width="90" />
          <el-table-column label="创建时间" width="150">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEditTag(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDeleteTag(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            :current-page="tagPage"
            :page-size="tagSize"
            :total="tags.length"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="(p: number) => (tagPage = p)"
            @size-change="
              (s: number) => {
                tagSize = s
                tagPage = 1
              }
            "
          />
        </div>
      </el-tab-pane>

      <!-- 分类 -->
      <el-tab-pane label="分类管理" name="categories">
        <div class="toolbar">
          <el-button type="primary" :icon="'Plus'" @click="openCreateCategory">新增分类</el-button>
        </div>
        <el-table v-loading="loading" :data="categories" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" min-width="120" />
          <el-table-column prop="slug" label="Slug" min-width="140" show-overflow-tooltip />
          <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="创建时间" width="150">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openEditCategory(row)"
                >编辑</el-button
              >
              <el-button link type="danger" size="small" @click="handleDeleteCategory(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 标签弹窗 -->
    <el-dialog
      v-model="tagDialogVisible"
      :title="tagEditing ? '编辑标签' : '新增标签'"
      width="420px"
    >
      <el-form ref="tagFormRef" :model="tagForm" :rules="tagRules" label-width="80px">
        <el-form-item label="标签名" prop="name">
          <el-input v-model="tagForm.name" placeholder="必填" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item label="Slug">
          <el-input v-model="tagForm.slug" placeholder="留空自动生成（中文自动兜底）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitTag">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分类弹窗 -->
    <el-dialog
      v-model="catDialogVisible"
      :title="catEditing ? '编辑分类' : '新增分类'"
      width="460px"
    >
      <el-form ref="catFormRef" :model="catForm" :rules="catRules" label-width="80px">
        <el-form-item label="分类名" prop="name">
          <el-input v-model="catForm.name" placeholder="必填" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item label="Slug">
          <el-input v-model="catForm.slug" placeholder="留空自动生成（中文自动兜底）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="catForm.description"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="catForm.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCategory">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style lang="scss" scoped>
.toolbar {
  margin-bottom: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
