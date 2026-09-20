<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createNotice,
  deleteNotice,
  getNotices,
  toggleNoticeStatus,
  updateNotice,
} from '@/api/system'
import type { Notice } from '@/api/types'

const list = ref<Notice[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref<Notice | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  title: '',
  content: '',
  status: 1 as 0 | 1,
  sortOrder: 0,
})
const rules: FormRules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 100, message: '最长 100 字符', trigger: 'blur' },
  ],
  content: [
    { required: true, message: '请输入内容', trigger: 'blur' },
    { max: 1000, message: '最长 1000 字符', trigger: 'blur' },
  ],
}

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const res = await getNotices()
    list.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.title = ''
  form.content = ''
  form.status = 1
  form.sortOrder = 0
  dialogVisible.value = true
}

function openEdit(row: Notice) {
  editing.value = row
  form.title = row.title
  form.content = row.content
  form.status = row.status as 0 | 1
  form.sortOrder = row.sortOrder
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  const payload = {
    title: form.title.trim(),
    content: form.content.trim(),
    status: form.status,
    sortOrder: form.sortOrder,
  }
  if (editing.value) {
    await updateNotice(editing.value.id, payload)
    ElMessage.success('已更新')
  } else {
    await createNotice(payload)
    ElMessage.success('已创建')
  }
  dialogVisible.value = false
  loadList()
}

async function handleToggle(row: Notice, next: boolean) {
  const status = next ? 1 : 0
  const text = next ? '发布' : '下架'
  try {
    await ElMessageBox.confirm(`确定${text}该公告？`, '操作确认', {
      type: 'info',
      confirmButtonText: text,
      cancelButtonText: '取消',
    })
    await toggleNoticeStatus(row.id, status as 0 | 1)
    ElMessage.success(`已${text}`)
    loadList()
  } catch {
    loadList()
  }
}

async function handleDelete(row: Notice) {
  await ElMessageBox.confirm(`确定删除公告「${row.title}」？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteNotice(row.id)
  ElMessage.success('已删除')
  loadList()
}

function formatTime(t?: string) {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-'
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button type="primary" :icon="'Plus'" @click="openCreate">发布公告</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-switch
            :model-value="row.status === 1"
            active-text="已发布"
            inline-prompt
            @change="(v: boolean) => handleToggle(row, v)"
          />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑公告' : '发布公告'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">发布</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
          <span class="tip">越大越靠前</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style lang="scss" scoped>
.toolbar {
  margin-bottom: 12px;
}
.tip {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}
</style>
