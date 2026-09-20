<script setup lang="ts">
import { RouterView, RouterLink, useRouter } from 'vue-router'
import { onBeforeUnmount, onMounted, ref, computed, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { http } from '@/api/request'
import { notificationApi } from '@/api/post'

const userStore = useUserStore()
const router = useRouter()
const keyword = ref('')
const menuOpen = ref(false)
const unread = ref(0)
let pollTimer: number | undefined
userStore.restore()

async function fetchUnread() {
  if (!userStore.isLoggedIn()) {
    unread.value = 0
    return
  }
  try {
    const res = await notificationApi.unreadCount()
    unread.value = res.data.count || 0
  } catch {
    /* 忽略轮询错误 */
  }
}

// ---------- WebSocket 实时通知（60s 轮询保留为兜底） ----------
let ws: WebSocket | undefined
let wsRetryTimer: number | undefined
let wsRetryDelay = 3000

function connectWs() {
  if (!userStore.isLoggedIn() || ws) return
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const token = userStore.accessToken || localStorage.getItem('accessToken') || ''
  try {
    ws = new WebSocket(`${proto}://${window.location.host}/api/ws/notify?token=${encodeURIComponent(token)}`)
  } catch {
    ws = undefined
    return
  }
  ws.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data) as { type?: string; title?: string; content?: string }
      if (msg.type !== 'notification') return
      unread.value += 1
      ElNotification({
        title: msg.title || '新通知',
        message: msg.content || '',
        type: 'info',
        duration: 4500,
      })
    } catch {
      /* 忽略非法消息 */
    }
  }
  ws.onopen = () => {
    wsRetryDelay = 3000
  }
  ws.onclose = () => {
    ws = undefined
    if (userStore.isLoggedIn()) scheduleWsRetry()
  }
  ws.onerror = () => {
    ws?.close()
  }
}

function scheduleWsRetry() {
  if (wsRetryTimer) return
  wsRetryTimer = window.setTimeout(() => {
    wsRetryTimer = undefined
    wsRetryDelay = Math.min(wsRetryDelay * 2, 60_000)
    connectWs()
  }, wsRetryDelay)
}

function closeWs() {
  if (wsRetryTimer) {
    clearTimeout(wsRetryTimer)
    wsRetryTimer = undefined
  }
  const sock = ws
  ws = undefined
  sock?.close()
}

onMounted(() => {
  fetchUnread()
  pollTimer = window.setInterval(fetchUnread, 60_000)
  connectWs()
})
onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
  closeWs()
  window.removeEventListener('app:unread-changed', fetchUnread)
})

watch(
  () => userStore.accessToken,
  (token) => {
    fetchUnread()
    if (token) connectWs()
    else closeWs()
  },
)
watch(
  () => router.currentRoute.value.fullPath,
  () => fetchUnread(),
)
window.addEventListener('app:unread-changed', fetchUnread)

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    try {
      await http.post('/auth/logout')
    } catch {
      /* 忽略 */
    }
    userStore.logout()
    unread.value = 0
    ElMessage.success('已退出')
    router.push('/login')
  } catch {}
}

function goSearch() {
  if (keyword.value.trim()) {
    router.push({ path: '/search', query: { q: keyword.value.trim() } })
  }
}

const navItems = computed(() => [
  { icon: 'HomeFilled', label: '推荐', to: '/' },
  { icon: 'Document', label: '文章', to: '/posts' },
  ...(userStore.isLoggedIn() ? [{ icon: 'EditPen', label: '写文章', to: '/posts/create' }] : []),
])
</script>

<template>
  <div class="app">
    <!-- ===== 顶部导航栏 ===== -->
    <header class="topbar">
      <div class="tb-inner">
        <!-- 品牌 -->
        <RouterLink to="/" class="brand">
          <div class="brand-mark">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <polyline points="16 18 22 12 16 6"></polyline>
              <polyline points="8 6 2 12 8 18"></polyline>
            </svg>
          </div>
          <span class="brand-name">CodeLink</span>
        </RouterLink>

        <!-- 主导航 -->
        <nav class="tb-nav">
          <RouterLink
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="tb-link"
            exact-active-class="on"
          >
            {{ item.label }}
          </RouterLink>
        </nav>

        <!-- 搜索 -->
        <div class="tb-search">
          <el-input
            v-model="keyword"
            placeholder="搜索文章..."
            size="default"
            clearable
            @keyup.enter="goSearch"
          >
            <template #prefix>
              <el-icon class="search-ic"><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <!-- 右侧：用户区 / 登录注册 -->
        <div class="tb-right">
          <template v-if="userStore.isLoggedIn()">
            <el-badge :value="unread" :hidden="!unread" :max="99" class="ntf-badge">
              <el-button text circle @click="router.push('/notifications')">
                <el-icon :size="20"><Bell /></el-icon>
              </el-button>
            </el-badge>
            <el-dropdown trigger="click">
              <div class="user-chip">
                <el-avatar :size="30" :src="userStore.userInfo?.avatar">
                  {{ userStore.userInfo?.username?.[0]?.toUpperCase() }}
                </el-avatar>
                <span class="u-name">{{ userStore.userInfo?.username }}</span>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="router.push('/me')">个人中心</el-dropdown-item>
                  <el-dropdown-item @click="router.push('/notifications')">通知</el-dropdown-item>
                  <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button text @click="router.push('/login')">登录</el-button>
            <el-button type="primary" size="small" @click="router.push('/register')"
              >注册</el-button
            >
          </template>

          <!-- 移动端菜单按钮 -->
          <el-button class="tb-burger" link @click="menuOpen = !menuOpen">
            <el-icon :size="22"><Menu v-if="!menuOpen" /><Close v-else /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 移动端展开菜单 -->
      <transition name="slide">
        <div v-if="menuOpen" class="tb-mobile">
          <RouterLink
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="m-link"
            exact-active-class="on"
            @click="menuOpen = false"
            >{{ item.label }}</RouterLink
          >
          <template v-if="!userStore.isLoggedIn()">
            <RouterLink to="/login" class="m-link" @click="menuOpen = false">登录</RouterLink>
            <RouterLink to="/register" class="m-link" @click="menuOpen = false">注册</RouterLink>
          </template>
        </div>
      </transition>
    </header>

    <!-- ===== 主内容 ===== -->
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: $paper;
}

// ========== 顶部导航栏 ==========
.topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid $border;

  .tb-inner {
    max-width: 1280px;
    margin: 0 auto;
    height: 56px;
    padding: 0 $s-5;
    display: flex;
    align-items: center;
    gap: $s-6;
  }
}

// 品牌
.brand {
  display: flex;
  align-items: center;
  gap: $s-2;
  text-decoration: none;
  flex-shrink: 0;

  .brand-mark {
    width: 32px;
    height: 32px;
    background: $brand;
    border-radius: $r-sm;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;

    svg {
      width: 18px;
      height: 18px;
    }
  }

  .brand-name {
    font-family: $font-display;
    font-size: 17px;
    font-weight: 700;
    color: $ink;
    letter-spacing: -0.01em;
  }
}

// 导航链接
.tb-nav {
  display: flex;
  gap: $s-1;
  flex-shrink: 0;

  .tb-link {
    padding: $s-1 $s-3;
    font-size: $fs-md;
    font-weight: 500;
    color: $ink-2;
    border-radius: $r-sm;
    transition: all 0.15s;

    &:hover {
      color: $ink;
      background: $surface-alt;
    }
    &.on {
      color: $brand;
      background: $brand-soft;
      font-weight: 600;
    }
  }
}

// 搜索
.tb-search {
  flex: 1;
  min-width: 0;

  :deep(.el-input__wrapper) {
    background: $surface-alt;
    box-shadow: none;
    border-radius: $r-full;
    padding: 2px $s-4;
  }

  :deep(.el-input__inner) {
    font-size: $fs-sm;
  }
  .search-ic {
    color: $ink-3;
  }
}

// 右侧用户区
.tb-right {
  display: flex;
  align-items: center;
  gap: $s-3;
  flex-shrink: 0;

  .user-chip {
    display: flex;
    align-items: center;
    gap: $s-2;
    padding: $s-1 $s-2 $s-1 $s-1;
    border-radius: $r-full;
    cursor: pointer;
    transition: background 0.15s;

    &:hover {
      background: $surface-alt;
    }

    .u-name {
      font-size: $fs-sm;
      font-weight: 500;
      color: $ink;
    }
  }

  .tb-burger {
    display: none;
  }
}

// 移动端展开
.tb-mobile {
  display: none;
  flex-direction: column;
  padding: $s-3 $s-5;
  gap: $s-1;
  border-top: 1px solid $border;
  background: $surface;

  .m-link {
    padding: $s-2 $s-3;
    font-size: $fs-md;
    font-weight: 500;
    color: $ink-2;
    border-radius: $r-sm;

    &:hover {
      color: $ink;
      background: $surface-alt;
    }
    &.on {
      color: $brand;
      background: $brand-soft;
    }
  }
}

.slide-enter-active,
.slide-leave-active {
  transition: all 0.2s ease;
}
.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

// ========== 主内容 ==========
.content {
  flex: 1;
  min-width: 0;
}

// ========== 响应式 ==========
@media (max-width: 768px) {
  .tb-inner {
    gap: $s-2;
    padding: 0 $s-3;
  }
  .tb-nav {
    display: none;
  }
  .brand .brand-name {
    display: none;
  }
  .tb-right {
    .u-name {
      display: none;
    }
    .tb-burger {
      display: flex;
    }
  }
  .tb-mobile {
    display: flex;
  }
}

@media (max-width: 480px) {
  .tb-inner {
    padding: 0 $s-2;
  }
  .tb-right .el-button {
    padding: 8px 10px;
  }
}
</style>
