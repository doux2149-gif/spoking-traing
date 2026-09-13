import { reactive } from 'vue'
import axios from 'axios'
import request from '../api/request'

export interface UserInfo {
  userId: number
  username: string
  nickname: string
  avatar: string | null
  email: string | null
  phone: string | null
  status: number
  roleId: number | null
  roleKey?: string
}

interface UserState {
  token: string
  userInfo: UserInfo | null
  menus: any[]
}

const state = reactive<UserState>({
  token: localStorage.getItem('token') || '',
  userInfo: (() => {
    const saved = localStorage.getItem('userInfo')
    if (saved) {
      try { return JSON.parse(saved) } catch { return null }
    }
    return null
  })(),
  menus: []
})

export function useUserStore() {
  const setToken = (token: string) => {
    state.token = token
    localStorage.setItem('token', token)
  }

  const setUserInfo = (info: UserInfo) => {
    state.userInfo = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  const setMenus = (menus: any[]) => {
    state.menus = menus
  }

  const login = async (username: string, password: string) => {
    const res = await request.post('/auth/login', { username, password })
    if (res.data) {
      setToken(res.data.token)
      setUserInfo({
        userId: res.data.userId,
        username: res.data.username,
        nickname: res.data.nickname,
        avatar: res.data.avatar,
        email: '',
        phone: '',
        status: 1,
        roleId: res.data.roleId,
        roleKey: res.data.roleKey
      })
      if (res.data.roleKey === 'admin') {
        await loadMenus()
      }
    }
    return res
  }

  const register = async (data: { username: string; password: string; email?: string; nickname?: string }) => {
    const res = await request.post('/auth/register', data)
    return res
  }

  const getUserInfo = async () => {
    const res = await request.get('/auth/user-info')
    if (res.data) {
      setUserInfo(res.data as UserInfo)
    }
    return res
  }

  const loadMenus = async () => {
    const res = await request.get('/auth/menus')
    if (res.data) {
      setMenus(res.data)
    }
    return res
  }

  /** 仅清本地登录态(401 强制下线时使用, 不再回调后端) */
  const clearLocalAuth = () => {
    state.token = ''
    state.userInfo = null
    state.menus = []
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  /** 主动退出: best-effort 通知后端删除在线会话, 无论成败都清本地态 */
  const logout = async () => {
    const token = state.token
    if (token) {
      try {
        // 用裸 axios 绕过响应拦截器, 避免 401 时递归弹登录
        await axios.post('/api/auth/logout', {}, {
          headers: { Authorization: `Bearer ${token}` },
          timeout: 8000
        })
      } catch {
        // 后端不可达或会话已失效都不阻塞本地退出
      }
    }
    clearLocalAuth()
  }

  const isLoggedIn = () => !!state.token

  const isAdmin = () => state.userInfo?.roleId === 1 || state.userInfo?.roleKey === 'admin'

  return {
    state,
    token: state.token,
    userInfo: state.userInfo,
    menus: state.menus,
    login,
    register,
    getUserInfo,
    loadMenus,
    logout,
    clearLocalAuth,
    isLoggedIn,
    isAdmin,
    setToken,
    setUserInfo,
    setMenus
  }
}
