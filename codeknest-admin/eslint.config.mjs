import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import skipFormatting from '@vue/eslint-config-prettier/skip-formatting'

export default defineConfigWithVueTs(
  // 全局忽略
  {
    name: 'app/ignores',
    ignores: ['dist/**', 'node_modules/**'],
  },
  // 检查范围
  {
    name: 'app/files-to-lint',
    files: ['**/*.{js,mjs,ts,mts,tsx,vue}'],
  },
  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,
  skipFormatting,
  {
    name: 'app/custom-rules',
    rules: {
      // 管理后台富文本/公告预览等由后端过滤后的受信 HTML 渲染，允许 v-html
      'vue/no-v-html': 'off',
      // 页面组件以 XxxPage 命名，天然多词，无需额外校验
      'vue/multi-word-component-names': 'off',
      // 存量 API 层/类型声明对响应体使用宽容 any，阶段 3 渐进收紧暂不阻断
      '@typescript-eslint/no-explicit-any': 'off',
      // vite-env.d.ts 的 DefineComponent<{}, {}, any> 为 Vue 官方模块声明模板写法
      '@typescript-eslint/no-empty-object-type': 'off',
    },
  },
)
