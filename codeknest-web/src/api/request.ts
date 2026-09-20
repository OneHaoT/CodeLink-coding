import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

// 统一响应结构
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  timestamp?: string
}

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

// 请求拦截器
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse
    // HTTP 层面可能拿到 200，但业务 code 非 200
    if (res && res.code !== 200 && res.code !== 201) {
      ElMessage.error(res.message || '请求失败')
      // Token 过期
      if (res.code === 40100 || res.code === 40101 || res.code === 40102) {
        const userStore = useUserStore()
        userStore.logout()
        window.location.href = '/login'
      }
      const bizErr = new Error(res.message) as Error & { bizCode?: number }
      bizErr.bizCode = res.code
      return Promise.reject(bizErr)
    }
    return res as unknown as never
  },
  (error) => {
    const msg = error?.response?.data?.message || error?.message || '网络异常'
    ElMessage.error(msg)
    if (error?.response?.status === 401) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

// 便捷请求方法
export const http = {
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return request.get(url, config) as unknown as Promise<ApiResponse<T>>
  },
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return request.post(url, data, config) as unknown as Promise<ApiResponse<T>>
  },
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return request.put(url, data, config) as unknown as Promise<ApiResponse<T>>
  },
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    return request.delete(url, config) as unknown as Promise<ApiResponse<T>>
  },
}

export default request
