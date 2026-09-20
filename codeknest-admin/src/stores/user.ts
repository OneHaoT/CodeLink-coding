import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface AdminUser {
  id: number
  username: string
  role: string
}

export const useUserStore = defineStore('user', () => {
  const accessToken = ref<string>(localStorage.getItem('adminAccessToken') || '')
  const refreshToken = ref<string>(localStorage.getItem('adminRefreshToken') || '')
  const userInfo = ref<AdminUser | null>(null)

  const isLoggedIn = () => !!accessToken.value

  function setAuth(access: string, refresh: string, info: AdminUser) {
    accessToken.value = access
    refreshToken.value = refresh
    userInfo.value = info
    localStorage.setItem('adminAccessToken', access)
    localStorage.setItem('adminRefreshToken', refresh)
    localStorage.setItem('adminUserInfo', JSON.stringify(info))
  }

  function logout() {
    accessToken.value = ''
    refreshToken.value = ''
    userInfo.value = null
    localStorage.removeItem('adminAccessToken')
    localStorage.removeItem('adminRefreshToken')
    localStorage.removeItem('adminUserInfo')
  }

  function restore() {
    const stored = localStorage.getItem('adminUserInfo')
    if (stored) {
      try {
        userInfo.value = JSON.parse(stored)
      } catch {}
    }
  }

  return { accessToken, refreshToken, userInfo, isLoggedIn, setAuth, logout, restore }
})
