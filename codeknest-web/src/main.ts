import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

import App from './App.vue'
import router from './router'
import ThumbsUpIcon from './components/ThumbsUpIcon.vue'
import './styles/index.scss'

// dayjs 扩展
dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const app = createApp(App)

// 注册所有 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component as any)
}

// 自建点赞图标（与 Element Plus 图标同样全局可用）
app.component('ThumbsUpIcon', ThumbsUpIcon)

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

app.mount('#app')
