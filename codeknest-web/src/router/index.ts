import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/DefaultLayout.vue'),
    children: [
      { path: '', name: 'home', component: () => import('@/views/home/HomePage.vue') },
      { path: 'posts', name: 'posts', component: () => import('@/views/post/PostListPage.vue') },
      {
        path: 'posts/:id',
        name: 'post-detail',
        component: () => import('@/views/post/PostDetailPage.vue'),
      },
      { path: 'search', name: 'search', component: () => import('@/views/search/SearchPage.vue') },
      {
        path: 'posts/create',
        name: 'post-create',
        component: () => import('@/views/post/PostEditorPage.vue'),
        meta: { requireAuth: true },
      },
      {
        path: 'posts/:id/edit',
        name: 'post-edit',
        component: () => import('@/views/post/PostEditorPage.vue'),
        meta: { requireAuth: true },
      },
      {
        path: 'users/:id',
        name: 'user-home',
        component: () => import('@/views/user/UserHomePage.vue'),
      },
      {
        path: 'me',
        name: 'me',
        component: () => import('@/views/user/MyProfilePage.vue'),
        meta: { requireAuth: true },
      },
      {
        path: 'me/favorites',
        name: 'me-favorites',
        component: () => import('@/views/user/MyFavoritesPage.vue'),
        meta: { requireAuth: true },
      },
      {
        path: 'me/drafts',
        name: 'me-drafts',
        component: () => import('@/views/user/MyDraftsPage.vue'),
        meta: { requireAuth: true },
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/views/notification/NotificationPage.vue'),
        meta: { requireAuth: true },
      },
    ],
  },
  { path: '/login', name: 'login', component: () => import('@/views/auth/LoginPage.vue') },
  { path: '/register', name: 'register', component: () => import('@/views/auth/RegisterPage.vue') },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/NotFound.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

// 路由守卫
router.beforeEach((to) => {
  const userStore = useUserStore()
  userStore.restore()
  if (to.meta.requireAuth && !userStore.isLoggedIn()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 已登录用户访问登录/注册页时直接回首页
  if ((to.name === 'login' || to.name === 'register') && userStore.isLoggedIn()) {
    return { path: '/' }
  }
})

export default router
