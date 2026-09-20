<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { getLoginLogs } from '@/api/system'
import type { LoginLog } from '@/api/types'

const filters = reactive({
  account: '',
  success: '' as '' | 'true' | 'false',
})
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref<LoginLog[]>([])
const loading = ref(false)

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const res = await getLoginLogs({
      page: page.value,
      size: size.value,
      account: filters.account || undefined,
      success: filters.success === '' ? undefined : filters.success === 'true',
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
  filters.account = ''
  filters.success = ''
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

function formatTime(t: string) {
  return dayjs(t).format('YYYY-MM-DD HH:mm:ss')
}
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" class="filter-bar">
      <el-form-item label="账号">
        <el-input
          v-model="filters.account"
          placeholder="昵称或邮箱"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="结果">
        <el-select v-model="filters.success" placeholder="全部" clearable style="width: 120px">
          <el-option label="成功" value="true" />
          <el-option label="失败" value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" border stripe row-key="id">
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'" size="small">
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="account" label="账号" min-width="140" show-overflow-tooltip />
      <el-table-column label="用户" width="120">
        <template #default="{ row }">
          {{ row.userId ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="140">
        <template #default="{ row }">{{ row.failReason || '-' }}</template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="140">
        <template #default="{ row }">{{ row.ip || '-' }}</template>
      </el-table-column>
      <el-table-column prop="userAgent" label="User-Agent" min-width="200" show-overflow-tooltip />
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
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
