<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { auditPost, deletePost, getPosts } from '@/api/post'
import { getCategories } from '@/api/meta'
import type { AdminPost, Category } from '@/api/types'

const route = useRoute()

const filters = reactive({
  q: '',
  status: '' as '' | 1 | 2 | 3,
  categoryId: '' as '' | number,
})
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref<AdminPost[]>([])
const loading = ref(false)
const categories = ref<Category[]>([])

const statusOptions = [
  { value: 1, label: '已发布' },
  { value: 2, label: '待审核' },
  { value: 3, label: '审核拒绝' },
]

// 审核弹窗
const auditVisible = ref(false)
const auditTarget = ref<AdminPost | null>(null)
const auditFormRef = ref<FormInstance>()
const auditForm = reactive<{ status: 1 | 3; reason: string }>({ status: 1, reason: '' })
const auditRules: FormRules = {
  reason: [
    {
      validator: (_r, v: string, cb) => {
        if (auditForm.status === 3 && !v.trim()) cb(new Error('拒绝时必须填写原因'))
        else cb()
      },
      trigger: 'blur',
    },
  ],
}

onMounted(async () => {
  if (route.query.status) {
    const s = Number(route.query.status)
    if ([1, 2, 3].includes(s)) filters.status = s as 1 | 2 | 3
  }
  const catRes = await getCategories()
  categories.value = catRes.data
  await loadList()
})

async function loadList() {
  loading.value = true
  try {
    const res = await getPosts({
      page: page.value,
      size: size.value,
      q: filters.q || undefined,
      status: filters.status === '' ? undefined : filters.status,
      categoryId: filters.categoryId === '' ? undefined : Number(filters.categoryId),
    })
    list.value = res.data.items
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadList()
}

function handleReset() {
  filters.q = ''
  filters.status = ''
  filters.categoryId = ''
  page.value = 1
  loadList()
}

function handlePageChange(p: number) {
  page.value = p
  loadList()
}

function handleSizeChange(s: number) {
  size.value = s
  page.value = 1
  loadList()
}

function openAudit(row: AdminPost, status: 1 | 3) {
  auditTarget.value = row
  auditForm.status = status
  auditForm.reason = ''
  auditVisible.value = true
}

async function submitAudit() {
  if (!auditTarget.value) return
  await auditFormRef.value?.validate()
  await auditPost(auditTarget.value.id, {
    status: auditForm.status,
    reason: auditForm.status === 3 ? auditForm.reason.trim() : undefined,
  })
  ElMessage.success(auditForm.status === 1 ? '已通过审核' : '已拒绝')
  auditVisible.value = false
  loadList()
}

async function handleDelete(row: AdminPost) {
  await ElMessageBox.confirm(`确定删除文章《${row.title}》？该操作不可恢复。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deletePost(row.id)
  ElMessage.success('已删除')
  loadList()
}

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm')
}

function statusTagType(s: number) {
  if (s === 1) return 'success'
  if (s === 2) return 'warning'
  return 'danger'
}

function statusText(s: number) {
  return statusOptions.find((o) => o.value === s)?.label ?? '未知'
}
</script>

<template>
  <div>
    <el-card shadow="never">
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.q"
            placeholder="标题/摘要"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option
              v-for="o in statusOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filters.categoryId" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="作者" width="120">
          <template #default="{ row }">{{ row.author?.username || '-' }}</template>
        </el-table-column>
        <el-table-column label="分类" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.category" size="small" type="info">{{ row.category.name }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{
              statusText(row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="浏览" width="70" prop="viewCount" />
        <el-table-column label="点赞" width="70" prop="likeCount" />
        <el-table-column label="评论" width="70" prop="commentCount" />
        <el-table-column label="收藏" width="70" prop="favoriteCount" />
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 1"
              link
              type="success"
              size="small"
              @click="openAudit(row, 1)"
            >
              通过
            </el-button>
            <el-button
              v-if="row.status !== 3"
              link
              type="warning"
              size="small"
              @click="openAudit(row, 3)"
            >
              拒绝
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="auditVisible" title="文章审核" width="460px">
      <el-form ref="auditFormRef" :model="auditForm" :rules="auditRules" label-width="80px">
        <el-form-item label="文章">
          <span>{{ auditTarget?.title }}</span>
        </el-form-item>
        <el-form-item label="审核结果">
          <el-radio-group v-model="auditForm.status">
            <el-radio :value="1">通过</el-radio>
            <el-radio :value="3">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="auditForm.status === 3" label="拒绝原因" prop="reason">
          <el-input
            v-model="auditForm.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAudit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.filter-bar {
  margin-bottom: 4px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
