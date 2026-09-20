<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteComment, getComments } from '@/api/comment'
import type { AdminComment } from '@/api/types'

const filters = reactive({
  q: '',
  postId: '' as '' | number,
  userId: '' as '' | number,
  status: '' as '' | 1,
})
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref<AdminComment[]>([])
const loading = ref(false)

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const res = await getComments({
      page: page.value,
      size: size.value,
      q: filters.q || undefined,
      postId: filters.postId === '' ? undefined : Number(filters.postId),
      userId: filters.userId === '' ? undefined : Number(filters.userId),
      status: filters.status === '' ? undefined : filters.status,
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
  filters.postId = ''
  filters.userId = ''
  filters.status = ''
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

async function handleDelete(row: AdminComment) {
  await ElMessageBox.confirm('确定删除该条评论？该操作不可恢复。', '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteComment(row.id)
  ElMessage.success('已删除')
  loadList()
}

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm')
}
</script>

<template>
  <div>
    <el-card shadow="never">
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.q"
            placeholder="评论内容"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="文章 ID">
          <el-input v-model="filters.postId" placeholder="postId" style="width: 110px" />
        </el-form-item>
        <el-form-item label="用户 ID">
          <el-input v-model="filters.userId" placeholder="userId" style="width: 110px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 110px">
            <el-option :value="1" label="正常" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="所属文章" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.postTitle || '-' }}</span>
            <span class="muted">#{{ row.postId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="评论人" width="130">
          <template #default="{ row }">
            <span>{{ row.username || '-' }}</span>
            <span class="muted">#{{ row.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="likeCount" label="点赞" width="70" />
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
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
.muted {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}
</style>
