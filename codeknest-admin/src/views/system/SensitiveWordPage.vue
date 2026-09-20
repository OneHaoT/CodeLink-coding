<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createSensitiveWord,
  deleteSensitiveWord,
  getSensitiveWords,
  updateSensitiveWord,
} from '@/api/system'
import type { SensitiveWord } from '@/api/types'

const filters = reactive({ word: '', category: '' })
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref<SensitiveWord[]>([])
const loading = ref(false)

// 弹窗
const dialogVisible = ref(false)
const editing = ref<SensitiveWord | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ word: '', category: '' })
const rules: FormRules = {
  word: [
    { required: true, message: '请输入敏感词', trigger: 'blur' },
    { max: 64, message: '最长 64 字符', trigger: 'blur' },
  ],
}

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const res = await getSensitiveWords({
      page: page.value,
      size: size.value,
      word: filters.word || undefined,
      category: filters.category || undefined,
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
  filters.word = ''
  filters.category = ''
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

function openCreate() {
  editing.value = null
  form.word = ''
  form.category = ''
  dialogVisible.value = true
}

function openEdit(row: SensitiveWord) {
  editing.value = row
  form.word = row.word
  form.category = row.category || ''
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  const payload = {
    word: form.word.trim(),
    category: form.category.trim() || null,
  }
  if (editing.value) {
    await updateSensitiveWord(editing.value.id, payload)
    ElMessage.success('已更新')
  } else {
    await createSensitiveWord(payload)
    ElMessage.success('已创建')
  }
  dialogVisible.value = false
  loadList()
}

async function handleDelete(row: SensitiveWord) {
  await ElMessageBox.confirm(`确定删除敏感词「${row.word}」？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteSensitiveWord(row.id)
  ElMessage.success('已删除')
  loadList()
}

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm')
}
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" class="filter-bar">
      <el-form-item label="敏感词">
        <el-input
          v-model="filters.word"
          placeholder="关键词"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="分类">
        <el-input
          v-model="filters.category"
          placeholder="分类"
          clearable
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="toolbar">
      <el-button type="primary" :icon="'Plus'" @click="openCreate">新增敏感词</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="word" label="敏感词" min-width="180" />
      <el-table-column label="分类" min-width="140">
        <template #default="{ row }">
          <el-tag v-if="row.category" size="small" type="info">{{ row.category }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑敏感词' : '新增敏感词'" width="420px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="敏感词" prop="word">
          <el-input v-model="form.word" placeholder="必填" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" placeholder="可选，如：政治/广告/辱骂" />
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
.filter-bar {
  margin-bottom: 4px;
}
.toolbar {
  margin-bottom: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
