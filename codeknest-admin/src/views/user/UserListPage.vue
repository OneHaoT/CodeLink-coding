<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getUsers, resetUserPassword, updateUserStatus } from '@/api/user'
import type { AdminUser } from '@/api/types'

const filters = reactive({
  q: '',
  status: '' as '' | 0 | 1,
})
const page = ref(1)
const size = ref(10)
const total = ref(0)
const list = ref<AdminUser[]>([])
const loading = ref(false)

// 重置密码弹窗
const pwVisible = ref(false)
const pwTarget = ref<AdminUser | null>(null)
const pwFormRef = ref<FormInstance>()
const pwForm = reactive({ newPassword: '', confirmPassword: '' })
const pwRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度须为 6-64 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_r, v: string, cb) => {
        if (v !== pwForm.newPassword) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur',
    },
  ],
}

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const res = await getUsers({
      page: page.value,
      size: size.value,
      q: filters.q || undefined,
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

async function handleStatusChange(row: AdminUser, next: number) {
  const actionText = next === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定${actionText}用户「${row.username}」？`, `${actionText}确认`, {
      type: next === 0 ? 'warning' : 'info',
      confirmButtonText: actionText,
      cancelButtonText: '取消',
    })
    await updateUserStatus(row.id, next === 0 ? 0 : 1)
    // 即时更新本地状态避免开关回弹，再拉取最新数据确认
    row.status = next
    ElMessage.success(`已${actionText}`)
    await loadList()
  } catch {
    // 用户取消或接口失败，恢复开关显示
    loadList()
  }
}

function openReset(row: AdminUser) {
  pwTarget.value = row
  pwForm.newPassword = ''
  pwForm.confirmPassword = ''
  pwVisible.value = true
}

async function submitReset() {
  if (!pwTarget.value) return
  await pwFormRef.value?.validate()
  await resetUserPassword(pwTarget.value.id, pwForm.newPassword)
  ElMessage.success('密码已重置')
  pwVisible.value = false
}

function roleTagType(role: string) {
  if (role === 'ROLE_ADMIN') return 'danger'
  if (role === 'ROLE_MODERATOR') return 'warning'
  return 'info'
}

function roleText(role: string) {
  return role.replace('ROLE_', '')
}

function formatTime(t: string | null) {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-'
}
</script>

<template>
  <div>
    <el-card shadow="never">
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.q"
            placeholder="用户名/邮箱"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 110px">
            <el-option :value="1" label="正常" />
            <el-option :value="0" label="禁用" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small">{{ roleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :disabled="row.role === 'ROLE_ADMIN'"
              active-text="正常"
              inline-prompt
              @change="(v: boolean) => handleStatusChange(row, v ? 1 : 0)"
            />
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="150">
          <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="注册时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              :disabled="row.role === 'ROLE_ADMIN'"
              @click="openReset(row)"
            >
              重置密码
            </el-button>
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

    <el-dialog v-model="pwVisible" title="重置密码" width="440px">
      <el-form ref="pwFormRef" :model="pwForm" :rules="pwRules" label-width="100px">
        <el-form-item label="目标用户">
          <span>{{ pwTarget?.username }}</span>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwForm.newPassword"
            type="password"
            show-password
            placeholder="6-64 位"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="pwForm.confirmPassword"
            type="password"
            show-password
            placeholder="再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReset">确定重置</el-button>
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
