import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/system/LoginPage.vue') },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requireAuth: true },
    children: [
      {
        path: '',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardPage.vue'),
        meta: { title: '仪表盘' },
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/views/user/UserListPage.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'posts',
        name: 'admin-posts',
        component: () => import('@/views/post/PostListPage.vue'),
        meta: { title: '文章管理' },
      },
      {
        path: 'comments',
        name: 'admin-comments',
        component: () => import('@/views/comment/CommentListPage.vue'),
        meta: { title: '评论管理' },
      },
      {
        path: 'tags',
        name: 'admin-tags',
        component: () => import('@/views/tag/TagListPage.vue'),
        meta: { title: '标签分类' },
      },
      {
        path: 'notices',
        name: 'admin-notices',
        component: () => import('@/views/system/NoticeListPage.vue'),
        meta: { title: '公告管理' },
      },
      {
        path: 'sensitive-words',
        name: 'admin-sensitive-words',
        component: () => import('@/views/system/SensitiveWordPage.vue'),
        meta: { title: '敏感词管理' },
      },
      {
        path: 'audit-logs',
        name: 'admin-audit-logs',
        component: () => import('@/views/system/AuditLogPage.vue'),
        meta: { title: '审计日志' },
      },
      {
        path: 'login-logs',
        name: 'admin-login-logs',
        component: () => import('@/views/system/LoginLogPage.vue'),
        meta: { title: '登录日志' },
      },
      { path: 'settings', redirect: '/' },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/system/NotFoundPage.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  userStore.restore()

  if (to.meta.requireAuth) {
    if (!userStore.isLoggedIn()) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    // V1 仅 ROLE_ADMIN 可进入管理后台
    if (userStore.userInfo?.role !== 'ROLE_ADMIN') {
      userStore.logout()
      return { name: 'login' }
    }
  }

  // 已登录管理员访问登录页时直接回首页
  if (to.name === 'login' && userStore.isLoggedIn() && userStore.userInfo?.role === 'ROLE_ADMIN') {
    return { path: '/' }
  }
})

export default router
