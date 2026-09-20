<script setup lang="ts">
/**
 * 统一点赞图标（Element Plus 官方图标库没有大拇指，故自建）
 * 线宽与 Element Plus 图标一致；filled 为 true 时填充，用于「已点赞」态
 */
withDefaults(defineProps<{ filled?: boolean }>(), { filled: false })
</script>

<template>
  <svg
    class="thumbs-up"
    viewBox="0 0 24 24"
    :fill="filled ? 'currentColor' : 'none'"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3z" />
    <path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3" />
  </svg>
</template>

<style scoped>
.thumbs-up {
  width: 1em;
  height: 1em;
  display: block;
}
</style>