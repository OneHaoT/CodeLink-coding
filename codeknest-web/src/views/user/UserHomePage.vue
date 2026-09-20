<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { postApi, userApi } from '@/api/post'
import type { PostVO, SimpleUser, UserActivityVO, UserHomeVO } from '@/api/types'
import { activityRoute, activityText } from '@/utils/activity'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const user = ref<UserHomeVO | null>(null)
const posts = ref<PostVO[]>([])
const activities = ref<UserActivityVO[]>([])
const followers = ref<SimpleUser[]>([])
const following = ref<SimpleUser[]>([])
const activeTab = ref<'posts' | 'activities' | 'followers' | 'following'>('posts')
const loading = ref(false)

const userId = computed(() => Number(route.params.id))
const isSelf = computed(() => userStore.userInfo?.id === userId.value)

async function loadUser() {
  const res = await userApi.home(userId.value)
  user.value = res.data
}

async function loadPosts() {
  loading.value = true
  try {
    const res = await postApi.list({ userId: userId.value, size: 50 })
    posts.value = res.data.items || []
  } finally {
    loading.value = false
  }
}

async function loadActivities() {
  loading.value = true
  try {
    const res = await userApi.activities(userId.value, { size: 30 })
    activities.value = res.data.items || []
  } finally {
    loading.value = false
  }
}

async function loadFollow() {
  loading.value = true
  try {
    const [f, g] = await Promise.all([
      userApi.followers(userId.value),
      userApi.following(userId.value),
    ])
    followers.value = f.data || []
    following.value = g.data || []
  } finally {
    loading.value = false
  }
}

async function toggleFollow() {
  if (!user.value) return
  const res = user.value.following
    ? await userApi.unfollow(user.value.id)
    : await userApi.follow(user.value.id)
  user.value.following = res.data.following
  user.value.followersCount = res.data.followersCount
}

watch(activeTab, (t) => {
  if (t === 'posts' && !posts.value.length) loadPosts()
  if (t === 'activities' && !activities.value.length) loadActivities()
  if (
    (t === 'followers' || t === 'following') &&
    !followers.value.length &&
    !following.value.length
  )
    loadFollow()
})
watch(userId, async () => {
  activeTab.value = 'posts'
  posts.value = []
  activities.value = []
  followers.value = []
  following.value = []
  await loadUser()
  await loadPosts()
})

function openActivity(a: UserActivityVO) {
  const to = activityRoute(a)
  if (to) router.push(to)
}

function time(t?: string) {
  return t ? dayjs(t).fromNow() : ''
}
function githubUrl(g: string) {
  return g.startsWith('http') ? g : `https://github.com/${g}`
}

onMounted(async () => {
  await loadUser()
  await loadPosts()
})
</script>

<template>
  <div v-if="user" class="uh-page">
    <!-- 用户信息头 -->
    <header class="uh-head">
      <el-avatar :size="72" :src="user.avatar || undefined" class="uh-avatar">
        {{ user.username[0] }}
      </el-avatar>
      <div class="uh-info">
        <div class="uh-name-row">
          <h1 class="uh-name">{{ user.username }}</h1>
          <el-tag v-if="user.role === 'ROLE_ADMIN'" size="small" type="warning">管理员</el-tag>
        </div>
        <p v-if="user.bio" class="uh-bio">{{ user.bio }}</p>
        <div class="uh-extra">
          <span v-if="user.location">
            <el-icon><Location /></el-icon>{{ user.location }}
          </span>
          <span v-if="user.company">
            <el-icon><OfficeBuilding /></el-icon>{{ user.company }}
          </span>
          <a v-if="user.website" :href="user.website" target="_blank" rel="noopener">
            <el-icon><Link /></el-icon>网站
          </a>
          <a v-if="user.github" :href="githubUrl(user.github)" target="_blank" rel="noopener">
            <el-icon><Link /></el-icon>GitHub
          </a>
        </div>
        <div class="uh-stats">
          <button class="stat" :class="{ on: activeTab === 'posts' }" @click="activeTab = 'posts'">
            <b>{{ user.postsCount }}</b> 文章
          </button>
          <button
            class="stat"
            :class="{ on: activeTab === 'activities' }"
            @click="activeTab = 'activities'"
          >
            动态
          </button>
          <button
            class="stat"
            :class="{ on: activeTab === 'followers' }"
            @click="activeTab = 'followers'"
          >
            <b>{{ user.followersCount }}</b> 粉丝
          </button>
          <button
            class="stat"
            :class="{ on: activeTab === 'following' }"
            @click="activeTab = 'following'"
          >
            <b>{{ user.followingCount }}</b> 关注
          </button>
        </div>
      </div>
      <div class="uh-actions">
        <el-button v-if="isSelf" type="primary" plain @click="router.push('/me')"
          >编辑资料</el-button
        >
        <el-button
          v-else-if="userStore.isLoggedIn()"
          :type="user.following ? 'default' : 'primary'"
          @click="toggleFollow"
        >
          {{ user.following ? '已关注' : '+ 关注' }}
        </el-button>
      </div>
    </header>

    <!-- Tab 内容 -->
    <div v-loading="loading">
      <!-- 文章 -->
      <div v-if="activeTab === 'posts'">
        <article
          v-for="p in posts"
          :key="p.id"
          class="p-card"
          @click="router.push(`/posts/${p.id}`)"
        >
          <h2 class="p-title">{{ p.title }}</h2>
          <p class="p-summary">{{ p.summary }}</p>
          <div class="p-foot">
            <div class="p-tags">
              <span v-for="t in p.tags || []" :key="t.id" class="tag">{{ t.name }}</span>
            </div>
            <div class="p-stats">
              <span>{{ time(p.publishedAt || p.createdAt) }}</span>
              <span><el-icon><View /></el-icon>{{ p.viewCount }}</span>
              <span><el-icon><ThumbsUpIcon /></el-icon>{{ p.likeCount }}</span>
              <span><el-icon><ChatDotRound /></el-icon>{{ p.commentCount }}</span>
            </div>
          </div>
        </article>
        <el-empty v-if="!loading && !posts.length" description="TA 还没有发布文章" />
      </div>

      <!-- 动态 -->
      <div v-else-if="activeTab === 'activities'" class="act-list">
        <div
          v-for="a in activities"
          :key="a.id"
          class="act-item"
          :class="{ linkable: !!activityRoute(a) }"
          @click="openActivity(a)"
        >
          <span class="act-dot" />
          <div class="act-body">
            <p class="act-text">{{ activityText(a) }}</p>
            <p v-if="a.postSummary && a.action !== 'COMMENT_CREATE'" class="act-summary">
              {{ a.postSummary }}
            </p>
            <span class="act-time">{{ time(a.createdAt) }}</span>
          </div>
        </div>
        <el-empty v-if="!loading && !activities.length" description="TA 还没有动态" />
      </div>

      <!-- 粉丝 / 关注 -->
      <div v-else>
        <div class="u-list">
          <div
            v-for="u in activeTab === 'followers' ? followers : following"
            :key="u.id"
            class="u-item"
            @click="router.push(`/users/${u.id}`)"
          >
            <el-avatar :size="44" :src="u.avatar || undefined">{{ u.username[0] }}</el-avatar>
            <span class="u-name">{{ u.username }}</span>
            <el-button size="small" text type="primary">查看主页</el-button>
          </div>
        </div>
        <el-empty
          v-if="!loading && !(activeTab === 'followers' ? followers : following).length"
          :description="activeTab === 'followers' ? '还没有粉丝' : '还没有关注任何人'"
        />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.uh-page {
  max-width: 800px;
  margin: 0 auto;
  padding: $s-6 $s-4 $s-10;
}

.uh-head {
  display: flex;
  gap: $s-5;
  align-items: flex-start;
  background: $surface;
  border: 1px solid $border;
  border-radius: $r-lg;
  padding: $s-6;
  margin-bottom: $s-6;

  .uh-avatar {
    flex-shrink: 0;
  }
  .uh-info {
    flex: 1;
    min-width: 0;
  }
  .uh-name-row {
    display: flex;
    align-items: center;
    gap: $s-3;
  }
  .uh-name {
    font-size: $fs-2xl;
    font-weight: 800;
    margin: 0;
  }
  .uh-bio {
    color: $ink-2;
    margin: $s-2 0;
    line-height: 1.6;
  }
  .uh-extra {
    display: flex;
    gap: $s-4;
    flex-wrap: wrap;
    font-size: $fs-sm;
    color: $ink-3;
    margin-bottom: $s-3;

    > span,
    > a {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }

    a {
      color: $brand;
      text-decoration: none;
    }
  }
  .uh-stats {
    display: flex;
    gap: $s-5;
    .stat {
      background: none;
      border: none;
      cursor: pointer;
      padding: 0;
      font-size: $fs-sm;
      color: $ink-3;
      b {
        font-size: $fs-lg;
        color: $ink;
        margin-right: 4px;
      }
      &.on b {
        color: $brand;
      }
    }
  }
}

.p-card {
  background: $surface;
  border: 1px solid $border;
  border-radius: $r-md;
  padding: $s-5;
  margin-bottom: $s-3;
  cursor: pointer;
  transition: all 0.15s;
  &:hover {
    border-color: $brand;
    transform: translateY(-1px);
  }

  .p-title {
    font-size: $fs-lg;
    font-weight: 700;
    margin: 0 0 $s-2;
  }
  .p-summary {
    color: $ink-3;
    margin: 0 0 $s-3;
    line-height: 1.6;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .p-foot {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: $s-2;
  }
  .p-tags {
    display: flex;
    gap: $s-2;
    .tag {
      padding: 2px $s-2;
      background: $brand-soft;
      color: $brand;
      font-size: $fs-sm;
      border-radius: $r-sm;
    }
  }
  .p-stats {
    display: flex;
    gap: $s-3;
    font-size: $fs-sm;
    color: $ink-3;

    span {
      display: inline-flex;
      align-items: center;
      gap: 3px;
    }
  }
}

.act-list {
  background: $surface;
  border: 1px solid $border;
  border-radius: $r-md;
  padding: $s-2 $s-5;
}

.act-item {
  display: flex;
  gap: $s-3;
  padding: $s-4 0;
  border-bottom: 1px solid $border;

  &:last-child {
    border-bottom: none;
  }
  &.linkable {
    cursor: pointer;
    &:hover .act-text {
      color: $brand;
    }
  }

  .act-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: $brand;
    flex-shrink: 0;
    margin-top: 7px;
  }
  .act-body {
    flex: 1;
    min-width: 0;
  }
  .act-text {
    margin: 0 0 $s-1;
    font-size: $fs-md;
    color: $ink;
    line-height: 1.6;
    transition: color 0.15s;
  }
  .act-summary {
    margin: 0 0 $s-1;
    font-size: $fs-sm;
    color: $ink-3;
    line-height: 1.6;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .act-time {
    font-size: $fs-xs;
    color: $ink-3;
  }
}

.u-list {
  background: $surface;
  border: 1px solid $border;
  border-radius: $r-md;
  .u-item {
    display: flex;
    align-items: center;
    gap: $s-3;
    padding: $s-4 $s-5;
    border-bottom: 1px solid $border;
    cursor: pointer;
    &:last-child {
      border-bottom: none;
    }
    .u-name {
      flex: 1;
      font-weight: 600;
    }
  }
}
</style>
