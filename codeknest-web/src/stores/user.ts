import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface UserInfo {
  id: number
  email?: string
  username: string
  avatar?: string
  role: string
}

export const useUserStore = defineStore('user', () => {
  const accessToken = ref<string>(localStorage.getItem('accessToken') || '')
  const refreshToken = ref<string>(localStorage.getItem('refreshToken') || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = () => !!accessToken.value
  const isAdmin = () => userInfo.value?.role === 'ROLE_ADMIN'

  function setAuth(access: string, refresh: string, info: UserInfo) {
    accessToken.value = access
    refreshToken.value = refresh
    userInfo.value = info
    localStorage.setItem('accessToken', access)
    localStorage.setItem('refreshToken', refresh)
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  function logout() {
    accessToken.value = ''
    refreshToken.value = ''
    userInfo.value = null
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('userInfo')
  }

  // 从 localStorage 恢复
  function restore() {
    const stored = localStorage.getItem('userInfo')
    if (stored) {
      try {
        userInfo.value = JSON.parse(stored)
      } catch {}
    }
  }

  return { accessToken, refreshToken, userInfo, isLoggedIn, isAdmin, setAuth, logout, restore }
})
