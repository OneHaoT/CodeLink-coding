<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { getActionLogs } from '@/api/system'
import type { UserActionLog } from '@/api/types'

// 与后端 UserActions 常量一一对应（动作 → 中文名）
const ACTIONS: { value: string; label: string }[] = [
  { value: 'LOGIN_SUCCESS', label: '登录成功' },
  { value: 'LOGIN_FAIL', label: '登录失败' },
  { value: 'LOGOUT', label: '退出登录' },
  { value: 'REGISTER', label: '注册账号' },
  { value: 'POST_PUBLISH', label: '发表文章' },
  { value: 'POST_DELETE', label: '删除文章' },
  { value: 'COMMENT_CREATE', label: '发表评论' },
  { value: 'COMMENT_DELETE', label: '删除评论' },
  { value: 'FOLLOW', label: '关注' },
  { value: 'UNFOLLOW', label: '取消关注' },
  { value: 'POST_LIKE', label: '点赞文章' },
  { value: 'POST_FAVORITE', label: '收藏文章' },
]
const actionLabel = (a: string) => ACTIONS.find((x) => x.value === a)?.label || a

const filters = reactive({
  action: '',
  account: '',
  range: [] as string[],
})
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref<UserActionLog[]>([])
const loading = ref(false)

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const res = await getActionLogs({
      page: page.value,
      size: size.value,
      action: filters.action || undefined,
      account: filters.account || undefined,
      start: filters.range?.[0] || undefined,
      end: filters.range?.[1] || undefined,
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
  filters.account = ''
  filters.range = []
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
      <el-form-item label="操作类型">
        <el-select v-model="filters.action" placeholder="全部" clearable style="width: 160px">
          <el-option v-for="a in ACTIONS" :key="a.value" :label="a.label" :value="a.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="账号">
        <el-input
          v-model="filters.account"
          placeholder="昵称或邮箱"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="时间">
        <el-date-picker
          v-model="filters.range"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" border stripe row-key="id">
      <el-table-column label="操作" width="110">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ actionLabel(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="account" label="账号" min-width="130">
        <template #default="{ row }">{{ row.account || '-' }}</template>
      </el-table-column>
      <el-table-column label="用户" width="90">
        <template #default="{ row }">{{ row.userId ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="详情" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ row.detail || '-' }}</template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.success == null" size="small" type="info">—</el-tag>
          <el-tag v-else :type="row.success ? 'success' : 'danger'" size="small">
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="120">
        <template #default="{ row }">{{ row.failReason || '-' }}</template>
      </el-table-column>
      <el-table-column label="IP" width="140">
        <template #default="{ row }">{{ row.ip || '-' }}</template>
      </el-table-column>
      <el-table-column label="User-Agent" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.userAgent || '-' }}</template>
      </el-table-column>
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