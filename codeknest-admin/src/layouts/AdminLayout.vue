<script setup lang="ts">
import { RouterView, useRouter, useRoute } from 'vue-router'
import { ref, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
userStore.restore()

const menus = [
  { path: '/', icon: 'DataAnalysis', title: '仪表盘' },
  { path: '/users', icon: 'User', title: '用户管理' },
  { path: '/posts', icon: 'Document', title: '文章管理' },
  { path: '/comments', icon: 'ChatDotRound', title: '评论管理' },
  { path: '/tags', icon: 'PriceTag', title: '标签分类' },
  { path: '/notices', icon: 'Bell', title: '公告管理' },
  { path: '/sensitive-words', icon: 'WarnTriangleFilled', title: '敏感词管理' },
  { path: '/audit-logs', icon: 'List', title: '审计日志' },
  { path: '/login-logs', icon: 'Key', title: '登录日志' },
]

const activeMenu = computed(() => route.path)

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
    userStore.logout()
    router.push('/login')
  } catch {}
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside :width="collapsed ? '64px' : '200px'" class="aside">
      <div class="logo">
        <el-icon><Connection /></el-icon>
        <span v-show="!collapsed">CodeLink Admin</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="menu"
        background-color="#001529"
        text-color="#ffffffa6"
        active-text-color="#fff"
      >
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <template #title>{{ m.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <el-icon class="collapse-btn" @click="collapsed = !collapsed">
          <Fold v-if="!collapsed" />
          <Expand v-else />
        </el-icon>
        <div class="header-right">
          <el-dropdown>
            <span class="user-info">
              <el-avatar :size="32">
                {{ userStore.userInfo?.username?.[0]?.toUpperCase() || 'A' }}
              </el-avatar>
              <span>{{ userStore.userInfo?.username || 'admin' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style lang="scss" scoped>
.admin-layout {
  height: 100vh;
}
.aside {
  background: #001529;
  transition: width 0.2s;
  overflow: hidden;
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 16px;
  font-weight: bold;
}
.menu {
  border-right: none;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>
