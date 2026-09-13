import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { useUserStore } from '../store/user'

const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000
})

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    const token = userStore.token
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

/** 401 跳转去重, 避免并发请求重复弹窗/跳转 */
let redirecting = false
function handleSessionExpired(message?: string) {
  const userStore = useUserStore()
  userStore.clearLocalAuth()
  ElMessage.error(message || '登录已过期，请重新登录')
  if (!redirecting) {
    redirecting = true
    router.push('/login').finally(() => { redirecting = false })
  }
}

request.interceptors.response.use(
  (response: AxiosResponse) => {
    // CSV 等 blob 下载: 直接返回完整 response, 由下载工具处理, 不走 Result 解包
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    if (res.code === 200) {
      return res
    }
    if (res.code === 401) {
      handleSessionExpired(res.message)
      return Promise.reject(new Error(res.message || '未登录或登录已过期'))
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    // blob 下载失败时后端 JSON 错误体在 Blob 里, 不弹 axios 原始文案, 交由调用方处理
    const isBlob = error.response?.config?.responseType === 'blob'
    const backendMsg = !isBlob ? error.response?.data?.message : undefined
    if (status === 401) {
      handleSessionExpired(backendMsg)
    } else if (status === 403) {
      ElMessage.error(backendMsg || '权限不足')
    } else if (!isBlob) {
      ElMessage.error(backendMsg || error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
