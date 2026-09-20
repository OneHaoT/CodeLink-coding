<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/post'
import type { UserHomeVO } from '@/api/types'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)

const form = reactive({
  username: '',
  bio: '',
  website: '',
  location: '',
  company: '',
  github: '',
  avatar: '',
})

async function load() {
  loading.value = true
  try {
    const res = await userApi.me()
    apply(res.data)
  } finally {
    loading.value = false
  }
}

function apply(u: UserHomeVO) {
  form.username = u.username || ''
  form.bio = u.bio || ''
  form.website = u.website || ''
  form.location = u.location || ''
  form.company = u.company || ''
  form.github = u.github || ''
  form.avatar = u.avatar || ''
}

async function save() {
  saving.value = true
  try {
    const res = await userApi.updateMe({ ...form })
    apply(res.data)
    // 同步本地登录态
    userStore.setAuth(userStore.accessToken, userStore.refreshToken, {
      id: res.data.id,
      username: res.data.username,
      avatar: res.data.avatar,
      role: res.data.role || userStore.userInfo?.role || 'ROLE_USER',
    })
    ElMessage.success('资料已保存')
  } finally {
    saving.value = false
  }
}

async function onAvatarChange(file: { raw: File }) {
  uploading.value = true
  try {
    const res = await userApi.uploadAvatar(file.raw)
    form.avatar = res.data.url
    userStore.setAuth(userStore.accessToken, userStore.refreshToken, {
      id: userStore.userInfo!.id,
      username: form.username || userStore.userInfo!.username,
      avatar: res.data.url,
      role: userStore.userInfo!.role,
    })
    ElMessage.success('头像已更新')
  } finally {
    uploading.value = false
  }
  return false
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="profile-page">
    <h1 class="page-title">
      <el-icon class="pt-ic"><Setting /></el-icon>
      <span>我的资料</span>
    </h1>

    <div class="profile-card">
      <!-- 头像 -->
      <div class="avatar-row">
        <el-avatar :size="80" :src="form.avatar || undefined">{{
          form.username[0] || '?'
        }}</el-avatar>
        <div class="avatar-side">
          <el-upload
            :show-file-list="false"
            :before-upload="onAvatarChange"
            accept="image/png,image/jpeg,image/gif,image/webp"
          >
            <el-button :loading="uploading">更换头像</el-button>
          </el-upload>
          <p class="avatar-tip">支持 jpg / png / gif / webp</p>
        </div>
      </div>

      <!-- 资料表单 -->
      <el-form label-position="top" class="profile-form">
        <el-form-item label="昵称">
          <el-input v-model="form.username" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item label="个人简介">
          <el-input
            v-model="form.bio"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="介绍一下你自己..."
          />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="所在城市">
            <el-input v-model="form.location" maxlength="128" placeholder="如：杭州" />
          </el-form-item>
          <el-form-item label="公司 / 组织">
            <el-input v-model="form.company" maxlength="128" />
          </el-form-item>
          <el-form-item label="个人网站">
            <el-input v-model="form.website" maxlength="512" placeholder="https://" />
          </el-form-item>
          <el-form-item label="GitHub">
            <el-input v-model="form.github" maxlength="128" placeholder="用户名或链接" />
          </el-form-item>
        </div>

        <div class="form-actions">
          <el-button @click="router.push('/me/drafts')">草稿箱</el-button>
          <el-button @click="router.push('/me/favorites')">我的收藏</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存资料</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.profile-page {
  max-width: 720px;
  margin: 0 auto;
  padding: $s-6 $s-4 $s-10;
}
.page-title {
  display: flex;
  align-items: center;
  gap: $s-2;
  font-size: $fs-3xl;
  font-weight: 800;
  margin: 0 0 $s-6;

  .pt-ic {
    color: $brand;
  }
}

.profile-card {
  background: $surface;
  border: 1px solid $border;
  border-radius: $r-lg;
  padding: $s-6;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: $s-5;
  margin-bottom: $s-6;
  .avatar-tip {
    font-size: $fs-sm;
    color: $ink-3;
    margin: $s-2 0 0;
  }
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 $s-4;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: $s-2;
  margin-top: $s-4;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
