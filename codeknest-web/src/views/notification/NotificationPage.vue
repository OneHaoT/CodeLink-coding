<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { notificationApi } from '@/api/post'
import type { NotificationVO } from '@/api/types'

const router = useRouter()
const list = ref<NotificationVO[]>([])
const loading = ref(false)
const filter = ref('')

const TYPE_META: Record<string, { icon: string; text: string }> = {
  LIKE_POST: { icon: 'ThumbsUpIcon', text: '赞了你的文章' },
  LIKE_COMMENT: { icon: 'ThumbsUpIcon', text: '赞了你的评论' },
  COMMENT_POST: { icon: 'ChatDotRound', text: '评论了你的文章' },
  REPLY_COMMENT: { icon: 'ChatLineRound', text: '回复了你的评论' },
  NEW_FOLLOWER: { icon: 'User', text: '关注了你' },
  SYSTEM: { icon: 'Bell', text: '系统通知' },
}

async function load() {
  loading.value = true
  try {
    const res = await notificationApi.list(filter.value || undefined)
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function markAll() {
  await notificationApi.markAllRead()
  ElMessage.success('已全部标为已读')
  list.value.forEach((n) => {
    n.isRead = true
  })
  window.dispatchEvent(new Event('app:unread-changed'))
}

async function openItem(n: NotificationVO) {
  if (!n.isRead) {
    await notificationApi.markRead(n.id)
    n.isRead = true
    window.dispatchEvent(new Event('app:unread-changed'))
  }
  if (n.sourcePostId) router.push(`/posts/${n.sourcePostId}`)
  else if (n.type === 'NEW_FOLLOWER' && n.sourceUserId) router.push(`/users/${n.sourceUserId}`)
}

function meta(n: NotificationVO) {
  return TYPE_META[n.type] || { icon: 'Bell', text: n.title || '新通知' }
}
function time(t: string) {
  return dayjs(t).fromNow()
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="ntf-page">
    <div class="ntf-head">
      <h1 class="page-title">
        <el-icon class="pt-ic"><Bell /></el-icon>
        <span>通知中心</span>
      </h1>
      <div class="head-ops">
        <el-radio-group v-model="filter" size="small" @change="load">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="LIKE_POST">赞</el-radio-button>
          <el-radio-button value="COMMENT_POST">评论</el-radio-button>
          <el-radio-button value="NEW_FOLLOWER">关注</el-radio-button>
        </el-radio-group>
        <el-button size="small" @click="markAll">全部已读</el-button>
      </div>
    </div>

    <div class="ntf-list">
      <div
        v-for="n in list"
        :key="n.id"
        class="ntf-item"
        :class="{ unread: !n.isRead }"
        @click="openItem(n)"
      >
        <el-avatar :size="40" :src="n.sourceUserAvatar || undefined" class="ntf-avatar">
          <el-icon><component :is="meta(n).icon" /></el-icon>
        </el-avatar>
        <div class="n-body">
          <div class="n-line">
            <b v-if="n.sourceUsername">{{ n.sourceUsername }}</b>
            <span>{{ meta(n).text }}</span>
            <span v-if="!n.isRead" class="dot"></span>
          </div>
          <p v-if="n.content" class="n-content">{{ n.content }}</p>
          <span class="n-time">{{ time(n.createdAt) }}</span>
        </div>
      </div>

      <el-empty v-if="!loading && !list.length" description="暂无通知" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.ntf-page {
  max-width: 720px;
  margin: 0 auto;
  padding: $s-6 $s-4 $s-10;
}
.ntf-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $s-5;
  gap: $s-3;
  flex-wrap: wrap;
  .page-title {
    display: flex;
    align-items: center;
    gap: $s-2;
    font-size: $fs-2xl;
    font-weight: 800;
    margin: 0;

    .pt-ic {
      color: $brand;
    }
  }
  .head-ops {
    display: flex;
    align-items: center;
    gap: $s-3;
  }
}

.ntf-list {
  background: $surface;
  border: 1px solid $border;
  border-radius: $r-lg;
  overflow: hidden;
}
.ntf-item {
  display: flex;
  gap: $s-3;
  padding: $s-4 $s-5;
  border-bottom: 1px solid $border;
  cursor: pointer;
  transition: background 0.15s;
  &:last-child {
    border-bottom: none;
  }
  &:hover {
    background: $brand-soft;
  }
  &.unread {
    background: rgba(64, 128, 255, 0.06);
  }

  .ntf-avatar {
    flex-shrink: 0;
  }
  .n-body {
    flex: 1;
    min-width: 0;
  }
  .n-line {
    display: flex;
    align-items: center;
    gap: $s-2;
    font-size: $fs-md;
    b {
      color: $ink;
    }
    .dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #f56c6c;
    }
  }
  .n-content {
    margin: $s-1 0 0;
    color: $ink-2;
    font-size: $fs-sm;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .n-time {
    font-size: $fs-sm;
    color: $ink-3;
  }
}
</style>
