<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { getAuditLogs } from '@/api/system'
import type { AuditLog } from '@/api/types'

const actionOptions = [
  'USER_ENABLE',
  'USER_DISABLE',
  'USER_PASSWORD_RESET',
  'POST_AUDIT',
  'POST_DELETE',
  'COMMENT_DELETE',
  'TAG_CREATE',
  'TAG_UPDATE',
  'TAG_DELETE',
  'CATEGORY_CREATE',
  'CATEGORY_UPDATE',
  'CATEGORY_DELETE',
  'SENSITIVE_CREATE',
  'SENSITIVE_UPDATE',
  'SENSITIVE_DELETE',
]

const filters = reactive({
  action: '',
  dateRange: [] as [string, string] | [],
})
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref<AuditLog[]>([])
const loading = ref(false)

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const res = await getAuditLogs({
      page: page.value,
      size: size.value,
      action: filters.action || undefined,
      startDate: filters.dateRange?.[0] || undefined,
      endDate: filters.dateRange?.[1] || undefined,
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
  filters.action = ''
  filters.dateRange = []
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

function actionLabel(a: string) {
  return a
    .toLowerCase()
    .split('_')
    .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
    .join(' ')
}
</script>

<template>
  <el-card shadow="never">
    <el-form :inline="true" class="filter-bar">
      <el-form-item label="操作类型">
        <el-select
          v-model="filters.action"
          placeholder="全部"
          clearable
          filterable
          style="width: 200px"
        >
          <el-option v-for="a in actionOptions" :key="a" :label="actionLabel(a)" :value="a" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" border stripe row-key="id">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="detail-box">
            <div><span class="d-label">详情：</span>{{ row.detail || '-' }}</div>
            <div><span class="d-label">对象：</span>{{ row.target }}</div>
            <div><span class="d-label">IP：</span>{{ row.ip }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="操作类型" min-width="160">
        <template #default="{ row }">
          <el-tag size="small">{{ actionLabel(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="operatorName" label="操作人" width="120">
        <template #default="{ row }">
          {{ row.operatorName }}<span class="muted">#{{ row.userId }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="target" label="操作对象" min-width="140" show-overflow-tooltip />
      <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
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
.muted {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}
.detail-box {
  padding: 8px 20px;
  color: #606266;
  font-size: 13px;
  line-height: 1.9;
}
.d-label {
  color: #909399;
}
</style>
