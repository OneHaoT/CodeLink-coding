<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { http } from '@/api/request'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const form = ref({ account: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  loading.value = true
  try {
    const res: any = await http.post('/auth/login', form.value)
    if (res.data.user.role !== 'ROLE_ADMIN') {
      ElMessage.error('该账号无管理员权限')
      return
    }
    userStore.setAuth(res.data.accessToken, res.data.refreshToken, {
      id: res.data.user.id,
      username: res.data.user.username,
      role: res.data.user.role,
    })
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.push(redirect)
  } catch {
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <template #header>
        <h2>CodeLink 管理后台</h2>
      </template>
      <el-form :model="form" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="账号">
          <el-input v-model="form.account" placeholder="管理员邮箱或昵称" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            show-password
          />
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" class="btn" @click="handleLogin"
          >登 录</el-button
        >
      </el-form>
      <div class="tip">仅限管理员账号登录</div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #001529;
}
.login-card {
  width: 400px;
}
.btn {
  width: 100%;
}
.tip {
  font-size: 12px;
  color: #909399;
  text-align: center;
  margin-top: 12px;
}
</style>
